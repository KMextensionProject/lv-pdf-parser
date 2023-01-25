package sk.gti.core;

import static javax.swing.JFileChooser.APPROVE_OPTION;
import static javax.swing.JFileChooser.FILES_ONLY;
import static javax.swing.JOptionPane.ERROR_MESSAGE;
import static javax.swing.JOptionPane.INFORMATION_MESSAGE;
import static javax.swing.JOptionPane.showMessageDialog;
import static sk.gti.core.XlsxUtils.DATE_TIME_PATTERN;
import static sk.gti.core.XlsxUtils.OPEN_XML_SUFFIX;

import java.awt.FlowLayout;
import java.io.File;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

public class Gui {

	public static void main(String[] args) {
		SwingUtilities.invokeLater(Gui::startGUI);
	}

	private static void startGUI() {
		JFrame frame = new JFrame();
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.setSize(300, 120);
		frame.setLocationRelativeTo(null);

		JPanel panel = new JPanel(new FlowLayout(FlowLayout.CENTER));
		panel.setSize(300, 120);

		JFileChooser fileChooser = new JFileChooser();
		fileChooser.setFileSelectionMode(FILES_ONLY);

		XlsxUtils xlsx = new XlsxUtils();

		final String buttonMainLabel = "Lookup LV";
		JButton button = new JButton();
		button.setText(buttonMainLabel);
		button.setHorizontalAlignment(SwingConstants.CENTER);
		button.addActionListener(e -> {
			int option = fileChooser.showOpenDialog(frame);
			if (option == APPROVE_OPTION) {
				File file = fileChooser.getSelectedFile();
				if (file.getName().endsWith("pdf")) {
					button.setText("Processing..");
					button.setEnabled(false);
					try {
						List<Map<String, Object>> xlsxDataSource = LVParser.fromPdf(file);
						showMessageDialog(frame, "LV has been successfully parsed! \nPlease choose the save location..",
							"Success", INFORMATION_MESSAGE);
						fileChooser.setSelectedFile(new File("vypis_z_katastra_" + LocalDateTime.now().format(DATE_TIME_PATTERN) + OPEN_XML_SUFFIX));
						option = fileChooser.showSaveDialog(frame);
						if (option == APPROVE_OPTION) {
							xlsx.exportToExcel("vypis_z_katastra_nehnutelnosti.xlsx", fileChooser.getSelectedFile().getAbsolutePath(), xlsxDataSource);
							showMessageDialog(frame, "Export successfull",
								"Success", INFORMATION_MESSAGE);
						}
					} catch (Exception e1) {
						showMessageDialog(frame, "Error generating report!\n" + e1.getMessage(), "Error", ERROR_MESSAGE);
						e1.printStackTrace();
					}
					button.setText(buttonMainLabel);
					button.setEnabled(true);
				} else {
					showMessageDialog(frame, "Invalid file type. Expected PDF", "Invalid file type", ERROR_MESSAGE);
				}
			}
		});
		panel.add(button);
		frame.add(panel);
		frame.setVisible(true);
	}
}
