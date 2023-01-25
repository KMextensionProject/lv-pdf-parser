package sk.gti.core;

import static java.lang.System.lineSeparator;
import static java.util.stream.Collectors.joining;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

public class LVParser {

	private static final Pattern SHARE_PATTERN = Pattern.compile("\\d\\/\\d");

	public static List<Map<String, Object>> fromPdf(File pdf) throws Exception {
		PDDocument doc = getPDDocumentWithoutWriteEncryption(pdf);
		String[] lines = getLinesForApartmentSection(doc);
		List<Map<String, Object>> lvData = new ArrayList<>(130);
		Set<String> errors = new LinkedHashSet<>();

		// lines[0] will always be an empty string
		for (int i = 1; i < lines.length; i++) {
			int verticalPartStartIndex = lines[i].indexOf("Poradové");
			String[] horizontalData = lines[i].substring(0, verticalPartStartIndex).split(lineSeparator());
			String[] verticalData = lines[i].substring(verticalPartStartIndex).split(lineSeparator());

			List<Map<String, Object>> list = new ArrayList<>();
			Map<String, Object> map = new HashMap<>();
			try {
				parseHorizontalPart(map, horizontalData);
				parseVerticalPart(list, map, verticalData);
			} catch (RuntimeException ex) {
				errors.add(stringifyStackTrace(ex));
				map.replaceAll((k, v) -> null);
				list.add(map);
			}
			lvData.addAll(list);
		}
		doc.close();

		if (!errors.isEmpty()) {
			logAsync(errors);
		}

		return lvData;
	}

	private static PDDocument getPDDocumentWithoutWriteEncryption(File pdf) throws IOException {
		PDDocument doc = PDDocument.load(pdf);
		if (doc.isEncrypted()) {
			doc.setAllSecurityToBeRemoved(true);
		}
		return doc;
	}

	private static String[] getLinesForApartmentSection(PDDocument doc) throws IOException {
		PDFTextStripper ps = new PDFTextStripper();
		String text = ps.getText(doc);
		text = text.substring(text.indexOf("Vchod (číslo)"), findApartmentEndIndex(text)); // get apartments only
//		every system line separator - https://docs.oracle.com/javase/8/docs/api/java/util/regex/Pattern.html#lineending
		text = text.replaceAll("\\d+\\s[z]\\s\\d+\\R", ""); // remove page numbers (etc. 12 z 73) - what if it is within some text?
		String[] lines = text.split("Vchod");
		return lines;
	}

	private static int findApartmentEndIndex(String text) {
		int index;
		if ((index = text.indexOf("Nebytové priestory")) > 0 
			|| (index = text.indexOf("ŤARCHY")) > 0) {
			return index;
		}
		return text.length() - 1;
	}

	private static void parseHorizontalPart(Map<String, Object> map, String[] data) {
		if (data[0].contains("Poschodie")) {
			String[] lineParts = data[1].split(" "); // Polna 19 8 33 castiach a spol..
			String vchod = lineParts[0] + " " + lineParts[1]; // Polna 19
			map.put("vchod", correctStreetName(vchod));
			map.put("poschodie", lineParts[2]);
			map.put("cislo_bytu", lineParts[3]);
			map.put("podiel_priestoru", data[4]);
			map.put("supisne_cislo", data[6]);
			map.put("ine_udaje", resolveOtherDataValue(data, 9));
		} else {
			map.put("vchod", correctStreetName(data[1]));
			map.put("poschodie", data[3]);
			map.put("cislo_bytu", data[5]);

			if (data[10].contains("Súpisné číslo")) {
				String podiel = data[10].replace("Súpisné číslo", "");
				map.put("podiel_priestoru", podiel);
				map.put("supisne_cislo", data[11]);
				map.put("ine_udaje", resolveOtherDataValue(data, 13));
			} else {
				map.put("podiel_priestoru", data[10]);
				map.put("supisne_cislo", data[12]);
				map.put("ine_udaje", resolveOtherDataValue(data, 14));
			}
		}
	}

	private static String correctStreetName(String street) {
		String[] streetParts;
		if (street.indexOf('.') < 0) {
			streetParts = street.split(" ");
		} else {
			streetParts = street.split("[.]");
		}
		// TODO: generalize this using dynamic programming method to avoid time costly lookups
		if (street.startsWith("Mosk")) {
			streetParts[0] = "Moskovská";
		}

		if (streetParts.length > 1) {
			StringBuilder sb = new StringBuilder();
			for (String streetPart : streetParts) {
				sb.append(streetPart).append(" ");
			}
			return sb.deleteCharAt(sb.length() - 1).toString();
		}
		// if the street name and its number are glued together
		else {
			// https://stackoverflow.com/questions/23436943/best-way-to-get-integer-end-of-a-string-in-java
			String[] nameAndNumberDivided = street.split("(?=\\d*$)", 2);
			if (nameAndNumberDivided.length > 1) {
				return nameAndNumberDivided[0] + " " + nameAndNumberDivided[1];
			}
		}
		return street;
	}

	private static String resolveOtherDataValue(String[] data, int index) {
		if (data[index].startsWith("In")) {
			return data[index + 1];
		}
		// if it does not, there is a seal message in red
		return data[index];
	}

	private static void parseVerticalPart(List<Map<String, Object>> list, Map<String, Object> map, String[] data) {
		parseVerticalPart(list, map, data, 7);
	}

	private static void parseVerticalPart(List<Map<String, Object>> list, Map<String, Object> map, String[] data, int startIndex) {
		int currentIndex = startIndex;
		if (data[0].contains("Titul")) {
			currentIndex = startIndex - 3;
		}
		String[] lineParts = data[currentIndex].split(" ");
		map.put("poradove_cislo", lineParts[0]);
		currentIndex++;

		map.put("vlastnik_skrateny", resolveOwnerShortName(data, lineParts, currentIndex));
		StringBuilder owner = new StringBuilder();
		for (int i = 1; i < lineParts.length; i++) {
			owner.append(lineParts[i])
				 .append(" ");
		}
		String share;
		String lastLinePart = lineParts[lineParts.length - 1];
		if (SHARE_PATTERN.matcher(lastLinePart).matches()) {
			owner.delete(owner.lastIndexOf(" "), owner.length()); // remove " 1/1"
			share = lastLinePart;
		} else {
			while(!SHARE_PATTERN.matcher(data[currentIndex]).matches()) {
				owner.append(data[currentIndex]);
				currentIndex++;
			}
			share = data[currentIndex];
			currentIndex++; // points to "Titul nadobudnutia" key
		}
		currentIndex++;
		map.put("vlastnik", owner);
		map.put("spoluvlastnicky_podiel", share);

		// take everything until "Ine udaje" section is found
		StringBuilder acquisition = new StringBuilder();
		while (!data[currentIndex].startsWith("In")) {
			acquisition.append(data[currentIndex]);
			currentIndex++;
		}
		map.put("titul_nadobudnutia", acquisition);
		// currentIdndex points to "Ine udaje" immediate value
		currentIndex++;
		map.put("ine_udaje_2", data[currentIndex]);

		list.add(map);

		// this way we know there is another person
		if (hasAnotherAcqusition(data, currentIndex)) {
			boolean hasNoMergeConflictWithNotes = false;
			for (; currentIndex < data.length; currentIndex++) {
				if (data[currentIndex].startsWith("Pozn") && data[currentIndex + 1].startsWith("Bez z")) {
					hasNoMergeConflictWithNotes = true;
					currentIndex += 2; // set the index on the owner line
					break;
				}
			}
			if (hasNoMergeConflictWithNotes) {
				// we must also duplicate the current data
				parseVerticalPart(list, new HashMap<>(map), data, currentIndex);
			} else {
				// clone map and add empty vertical part into the list
				list.add(fillAndGetUnparsableData(map, null));
			}
		}
	}

	private static String resolveOwnerShortName(String[] data, String[] lineParts, int currentIndex) {
		if (!data[currentIndex - 1].contains("IČO")) {
			String firstName = leadingCharInUpperCase(lineParts[1]);
			String lastName = leadingCharInUpperCase(lineParts[2]);
			if (lastName.endsWith(",")) {
				lastName = lastName.substring(0, lastName.length() - 1);
			}
			return firstName + " " + lastName;
		}
		return "Inštitúcia";
	}

	private static String leadingCharInUpperCase(String text) {
		char leadingChar = Character.toUpperCase(text.charAt(0));
		return leadingChar + text.substring(1).toLowerCase();
	}

	private static boolean hasAnotherAcqusition(String[] data, int currentIndex) {
		// find another acquisition which will indicate the presence of another person
		int nextAcqusitionIndex = -1;
		for (int i = currentIndex; i < data.length; i++) {
			if (data[i].startsWith("Titul na")) {
				nextAcqusitionIndex = i;
				break;
			}
		}
		return nextAcqusitionIndex >= 0;
	}

	private static Map<String, Object> fillAndGetUnparsableData(Map<String, Object> source, String substitutionValue) {
		Map<String, Object> unparsableData = new HashMap<>(source);
		unparsableData.put("poradove_cislo", substitutionValue);
		unparsableData.put("vlastnik_skrateny", substitutionValue);
		unparsableData.put("vlastnik", substitutionValue);
		unparsableData.put("spoluvlastnicky_podiel", substitutionValue);
		unparsableData.put("titul_nadobudnutia", substitutionValue);
		unparsableData.put("ine_udaje_2", substitutionValue);
		return unparsableData;
	}

	private static String stringifyStackTrace(Exception ex) {
		return lineSeparator() + "[ ERROR ] " + ex.getClass() + " - " +
			Arrays.stream(ex.getStackTrace())
				  .map(String::valueOf)
				  .collect(joining(lineSeparator()));
	}

	private static void logAsync(Collection<String> errorMessages) {
		CompletableFuture.runAsync(() -> {
			String userDir = System.getProperty("user.dir") + "/" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy_MM_dd"));
			String logFile = userDir + "_errors.log";
			try {
				Files.write(Paths.get(logFile), errorMessages);
			} catch (IOException e) {
				// ignore
			}
		});
	}
}
