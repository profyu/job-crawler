package com.example.jobcrawler;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.NumberFormat;
import java.util.regex.Pattern;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public class App {
	public static void printHelp(Options options) {
		HelpFormatter formatter = new HelpFormatter();
		formatter.printHelp("jobc <options>", options);
	}

	public static void main(String[] args) {

		Options options = new Options();
		options.addOption("p", "max-page", true, "最大頁數");
		options.addOption("o", "output-dir", true, "CSV檔輸出路徑");
		options.addOption("h", "help", false, "印出CLI說明");

		DefaultParser parser = new DefaultParser();

		try {

			CommandLine cmdLine = parser.parse(options, args);

			if (cmdLine.hasOption('h')) {
				printHelp(options);
				System.exit(0);
				
			}

			if (!cmdLine.hasOption('p')) {
				System.out.println("未提供最大頁數");
				printHelp(options);
				System.exit(1);
			}

			if (!cmdLine.hasOption('o')) {
				System.out.println("未提供CSV檔輸出路徑");
				printHelp(options);
				System.exit(1);
			}

			int maxPage = Integer.parseInt(cmdLine.getOptionValue('p'));

			File outputFile = new File(cmdLine.getOptionValue('o'));
			File outputDir = outputFile.getParentFile();
			outputDir.mkdirs();

			try (CSVPrinter csvPrinter = new CSVPrinter(new FileWriter(outputFile),
					CSVFormat.EXCEL.withHeader("職務", "雇主", "位置", "標籤", "最低月薪", "最高月薪", "幣別"))) {
				NumberFormat numberFormat = NumberFormat.getNumberInstance(java.util.Locale.US);
				for (int i = 1; i <= maxPage; i++) {
					try {
						Document doc = Jsoup.connect("https://mit.jobs/jobs?page=" + i).get();
						Elements jobItems = doc.select("#job-list .job-item");
						for (Element jobItem : jobItems) {

							// 職務
							Element titleElement = jobItem.selectFirst("a.job-title");
							if (titleElement == null) {
								continue;
							}
							String title = titleElement.text().trim();

							// 雇主
							Element employerElement = jobItem.selectFirst("a.job-employer");
							if (employerElement == null) {
								continue;
							}
							String employer = employerElement.text().trim();

							// 位置
							Element locationElement = jobItem.selectFirst("div.location");
							if (locationElement == null) {
								continue;
							}
							String location = locationElement.text().trim();

							// 標籤
							Elements tagElements = jobItem.select("div.tags a.tag");
							String tags = "";
							for (Element tagElement : tagElements) {
								tags += tagElement.text();
							}

							// 月薪資
							Element payElement = jobItem.selectFirst("span.pay");
							if (payElement == null) {
								continue;
							}
							String rawPay = payElement.text().trim();
							float minPay = 0;
							float maxPay = 0;

							if (rawPay.contains(" - ")) {
								String[] splitted = rawPay.split(" - ");
								minPay = numberFormat.parse(splitted[0]).floatValue();
								maxPay = numberFormat.parse(splitted[1]).floatValue();
							} else if (rawPay.endsWith("+")) {
								String sliced = rawPay.substring(0, rawPay.length() - 1);
								minPay = numberFormat.parse(sliced).floatValue();
							} else {
								continue;
							}

							// 薪資細節
							Element paidByElement = jobItem.selectFirst("div.paidby");
							if (paidByElement == null) {
								continue;
							}
							String rawPaidBy = paidByElement.text().trim();
							if (rawPaidBy.contains("per annum")) {
								minPay /= 12f;
								maxPay /= 12f;
							} else if (!rawPaidBy.contains("per month")) {
								continue;
							}

							// 幣別
							String[] splittedPaidBy = rawPaidBy.split(" ");
							String currency = splittedPaidBy[0];

							csvPrinter.printRecord(title, employer, location, tags, minPay, maxPay, currency);

						}
						Thread.sleep(1000);
					} catch (IOException ioe) {
						ioe.printStackTrace();
						break;
					}

				}
				csvPrinter.flush();

			}

		} catch (Exception ex) {
			ex.printStackTrace();
			printHelp(options);
		}
	}
}
