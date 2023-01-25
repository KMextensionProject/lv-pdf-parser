package sk.gti.core;

import java.io.FileOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.poi.ss.usermodel.Workbook;

import net.sf.jett.transform.ExcelTransformer;

public class XlsxUtils {

	public static final DateTimeFormatter DATE_TIME_PATTERN = DateTimeFormatter.ofPattern("yyyyMMdd_HH_mm");
	public static final String OPEN_XML_SUFFIX = ".xlsx";

	public void exportToExcel(String template, String outputName, List<Map<String, Object>> data) throws Exception {
		Map<String, Object> dataSource = new HashMap<>();
		dataSource.put("table", data);

		ExcelTransformer transformer = new ExcelTransformer();
		Workbook workbook = transformer.transform(getClass().getClassLoader().getResourceAsStream(template), dataSource);
		workbook.write(new FileOutputStream(resolveFileName(outputName)));
		workbook.close();
	}

	private String resolveFileName(String fileName) {
		if (fileName == null) {
			return "export_" + LocalDateTime.now().format(DATE_TIME_PATTERN) + OPEN_XML_SUFFIX; 
		} else if (!fileName.endsWith(OPEN_XML_SUFFIX)) {
			return removeCurrentSuffix(fileName) + OPEN_XML_SUFFIX;
		}
		return fileName;
	}

	private String removeCurrentSuffix(String fileName) {
		int lastDotIndex = fileName.lastIndexOf('.');
		if (lastDotIndex >= 0) {
			return fileName.substring(0, lastDotIndex);
		}
		return fileName;
	}
}
