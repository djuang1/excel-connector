package org.mule.modules.excel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.List;

import org.mule.api.annotations.Connector;
import org.mule.api.annotations.display.Summary;
import org.mule.api.annotations.Processor;
import org.mule.api.annotations.param.Default;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FilenameFilter;
import java.io.IOException;

import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

@Connector(name="excel", friendlyName="Excel")
public class ExcelConnector {
	
	public static final int EXCEL_STYLE_ESCAPING = 0;
	
    private Workbook workbook = null;
    private List<List<String>> excelData = null;
    private int maxRowWidth = 0;
    private int formattingConvention = 0;
    private DataFormatter formatter = null;
    private FormulaEvaluator evaluator = null;
    private String separator = ",";
    private boolean includesHeader = true;
    
    /**
     * Custom processor
     *
     * {@sample.xml ../../../doc/excel-connector.xml.sample excel:readexcel}
     *
     * @param fileName Excel file location.
     * @param sheetName Sheet in Excel file to retrieve data.
     * @param fileIncludesHeaderRow Headers are included in file.
     * @return Data in List format.
     */
    @Processor
    @Summary("Read Excel File")
    public List<Map<String,String>> readExcel(String fileName, String sheetName, @Default("true") boolean fileIncludesHeaderRow) throws FileNotFoundException, IOException, IllegalArgumentException, InvalidFormatException{
    	
    	File source = new File(fileName);
    	File[] filesList = null;
    	this.includesHeader = fileIncludesHeaderRow;
    	
    	if(source.isDirectory()) {
            filesList = source.listFiles(new ExcelFilenameFilter());
        }
        else {
            filesList = new File[]{source};
        }
    	
    	for(File excelFile : filesList) {
            this.openWorkbook(excelFile);
            this.convertSheetToMap(sheetName); 
        }
    	
    	return generateMap(fileIncludesHeaderRow);
    }
    
    private void convertSheetToMap(String sheetName) {
        Sheet sheet = null;
        Row row = null;
        int lastRowNum = 0;
        this.excelData = new ArrayList<List<String>>(); 
        
        int numSheets = this.workbook.getNumberOfSheets();

        for(int i = 0; i < numSheets; i++) {
        	
        	if (this.workbook.getSheetName(i).equals(sheetName)){
        		sheet = this.workbook.getSheetAt(i);
                if(sheet.getPhysicalNumberOfRows() > 0) {

                    lastRowNum = sheet.getLastRowNum();
                    for(int j = 0; j <= lastRowNum; j++) {
                        row = sheet.getRow(j);
                        this.rowToArrayList(row);
                    }
                }
        	}
        }
    }
    
    private void rowToArrayList(Row row) {
        Cell cell = null;
        int lastCellNum = 0;
        ArrayList<String> excelLine = new ArrayList<String>();

        if(row != null) {
            lastCellNum = row.getLastCellNum();
            for(int i = 0; i <= lastCellNum - 1; i++) {            	            	
                cell = row.getCell(i);
                if(cell == null) {  
                	excelLine.add("");
                }
                else {
                    if(cell.getCellType() != Cell.CELL_TYPE_FORMULA) {
                    	excelLine.add(this.formatter.formatCellValue(cell));
                    }
                    else {
                    	excelLine.add(this.formatter.formatCellValue(cell, this.evaluator));
                    }
                }
            }
            
            if(lastCellNum > this.maxRowWidth) {
                this.maxRowWidth = lastCellNum;
            }
        }
        this.excelData.add(excelLine);
    }
    
    private void openWorkbook(File file) throws FileNotFoundException,
	    IOException, InvalidFormatException {
		FileInputStream fis = null;
		try {
			fis = new FileInputStream(file);
			
			this.workbook = WorkbookFactory.create(fis);
			this.evaluator = this.workbook.getCreationHelper().createFormulaEvaluator();
			this.formatter = new DataFormatter(true);
		}
		finally {
			if(fis != null) {
				fis.close();
			}
		}
	}
    
    private List<Map<String,String>> generateMap(boolean fileIncludesHeaderRow) {
    	
    	List<Map<String, String>> list = new ArrayList<>();
    	List<String> line = null;
    	String csvLineElement = null;
    	
    	for(int i = 0; i < this.excelData.size(); i++) {
    		
    		if(fileIncludesHeaderRow && i == 0){
    			continue;
    		}
    		
    		line = this.excelData.get(i);
    		
    		Map<String, String> row = new HashMap<>();  
    		
    		for(int j = 0; j < this.maxRowWidth; j++) {
    			
    			String colName = "Column";
    					
    			if(fileIncludesHeaderRow){

    				// if there are more columns than headers, create column name (Column#)
    				if ((j + 1) > this.excelData.get(0).size()){
    					colName += (j + 1);
    				} else {
        				// if column is not blank or empty, set column name to column
        				if (this.excelData.get(0).get(j) != null && !this.excelData.get(0).get(j).isEmpty()){
            				colName = this.excelData.get(0).get(j);
            			} else {
            				colName += (j + 1);            				
            			}
    				}    				
    			} else {
    				colName += (j + 1);    			
    			}
    			
    			if(line.size() > j) {
    				csvLineElement = line.get(j);
    				if(csvLineElement != null) {            						
            			row.put(colName, escapeEmbeddedCharacters(line.get(j)));			
    				} else {
    					row.put(colName, "");   
    				}    				
    			}
    			if(line.size() <= j) {    				
        			row.put(colName, "");
    			}  
    			
    		}
    		list.add(row);
    	}
    	   	
    	return list;
    }
        
    private String escapeEmbeddedCharacters(String field) {
        StringBuffer buffer = null;

        if(this.formattingConvention == ExcelConnector.EXCEL_STYLE_ESCAPING) {

            if(field.contains("\"")) {
                buffer = new StringBuffer(field.replaceAll("\"", "\\\"\\\""));
                buffer.insert(0, "\"");
                buffer.append("\"");
            }
            else {
                buffer = new StringBuffer(field);
                if((buffer.indexOf(this.separator)) > -1 ||
                         (buffer.indexOf("\n")) > -1) {
                    buffer.insert(0, "\"");
                    buffer.append("\"");
                }
            }
            return(buffer.toString().trim());
        }

        else {
            if(field.contains(this.separator)) {
                field = field.replaceAll(this.separator, ("\\\\" + this.separator));
            }
            if(field.contains("\n")) {
                field = field.replaceAll("\n", "\\\\\n");
            }
            return(field);
        }
    }
    
    
    class ExcelFilenameFilter implements FilenameFilter {
        public boolean accept(File file, String name) {
            return(name.endsWith(".xls") || name.endsWith(".xlsx"));
        }
    }

}