package catena.parser;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactoryConfigurationError;

import org.xml.sax.SAXException;

import catena.parser.entities.EntityEnum;
import catena.parser.entities.TimeMLDoc;
import catena.parser.entities.Timex;
import edu.stanford.nlp.simple.*;

public class TimeMLToColumns {
	
	private EntityEnum.Language language;
	private Map<Integer, Integer> startOfSentences;
	
	public TimeMLToColumns() {
		startOfSentences = new HashMap<Integer, Integer>();
	}
	
	public TimeMLToColumns(EntityEnum.Language lang) {
		this.setLanguage(lang);
		startOfSentences = new HashMap<Integer, Integer>();
	}

	public EntityEnum.Language getLanguage() {
		return language;
	}

	public void setLanguage(EntityEnum.Language language) {
		this.language = language;
	}
	
	public List<String> parse(String timeMLFilePath) throws ParserConfigurationException, SAXException, IOException, TransformerFactoryConfigurationError, TransformerException {
		List<String> columns = new ArrayList<String>();
		
		//Get <TEXT> content from TimeML document
		TimeMLParser tmlParser = new TimeMLParser(EntityEnum.Language.EN);
		TimeMLDoc tmlDoc = new TimeMLDoc(timeMLFilePath);
		String tmlText = tmlParser.getText(tmlDoc);
//		System.out.println(tmlText);			
		String tmlTextOnly = tmlParser.getTextOnly(tmlDoc);
//		System.out.println(tmlTextOnly);
		
		//Get <MAKEINSTANCE> map (eventID to tense, aspect, and polarity) from TimeML document
		Map<String, String> mapInstances = tmlParser.getEventTenseAspectPolarity(tmlDoc);
		
//		//Run TextPro parser on the content string
//		TextProParser textpro = new TextProParser("./tools/TextPro2.0/");
//		String[] annotations = {"token"};
//		String result = textpro.run(annotations, tmlText);
//		System.out.println(result);
		
		//DCT into columns
		Timex dct = tmlParser.getDCT(tmlDoc);
		columns.add("DCT_" + dct.getValue() 
				+ "\t" + "O" 
				+ "\t" + "O"
				+ "\t" + "O"
				+ "\t" + "O"
				+ "\t" + "O"
				+ "\t" + "O"
				+ "\t" + dct.getID()
				+ "\t" + dct.getType()
				+ "\t" + dct.getValue()
				+ "\t" + "O"
				+ "\t" + "O");
		columns.add("");
		
		//Run Stanford parser on the content string (with tags)
		List<String> tokensWithTags = new ArrayList<String>();
		Document doc = new Document(tmlText);
		for (Sentence sent : doc.sentences()) {
			for (String word : sent.words()) {
				if (word.equals("``")) tokensWithTags.add("\"");
				else if (word.equals("''")) tokensWithTags.add("\"");
				else tokensWithTags.add(word);
			}
			tokensWithTags.add("");
        }
		
		//Run Stanford parser on the content string (text only)
		List<String> tokens = new ArrayList<String>();
		List<String> lemmas = new ArrayList<String>();
		doc = new Document(tmlTextOnly);
		for (Sentence sent : doc.sentences()) {
			for (String word : sent.words()) {
				if (word.equals("``")) tokens.add("\"");
				else if (word.equals("''")) tokens.add("\"");
				else tokens.add(word);
			}
			for (String lemma : sent.lemmas()) {
				if (lemma.equals("``")) lemmas.add("\"");
				else if (lemma.equals("''")) lemmas.add("\"");
				else lemmas.add(lemma);
			}
			tokens.add("");
			lemmas.add("");
        }
		
		
		int i = 0, j = 0, sent = 1, idx = 1;
		String evId = "O", evClass = "O";
		String tmxId = "O", tmxType = "O", tmxValue = "O";
		String tenseAspectPolarity = "O";
		String sigId = "O", csigId = "O";
		
		startOfSentences.put(sent, idx);
		
		while (i < tokensWithTags.size()) {
			if (tokens.get(j).equals("")) {
				columns.add(tokens.get(j));
				i ++;
				j ++;
				sent ++;
				startOfSentences.put(sent, idx);
			} else {
				if (tokensWithTags.get(i).equals(tokens.get(j))) {
					columns.add(tokens.get(j) 
							+ "\t" + "t" + idx 
							+ "\t" + sent
							+ "\t" + lemmas.get(j)
							+ "\t" + evId
							+ "\t" + evClass
							+ "\t" + tenseAspectPolarity
							+ "\t" + tmxId
							+ "\t" + tmxType
							+ "\t" + tmxValue
							+ "\t" + sigId
							+ "\t" + csigId);
					i ++;
					j ++;
					idx ++;
				} else {
					if (tokensWithTags.get(i).equals(".")) i ++;
					else if (tokensWithTags.get(i).equals("")) i ++;
					else if (tokensWithTags.get(i).equals("</EVENT>")) {
						evId = "O";
						evClass = "O";
						tenseAspectPolarity = "O";
						i ++;
					}
					else if (tokensWithTags.get(i).equals("</TIMEX3>")) {
						tmxId = "O";
						tmxType = "O";
						tmxValue = "O";
						i ++;
					}
					else if (tokensWithTags.get(i).equals("</SIGNAL>")) {
						sigId = "O";
						i ++;
					}
					else if (tokensWithTags.get(i).equals("</C-SIGNAL>")) {
						csigId = "O";
						i ++;
					}
					else if (tokensWithTags.get(i).contains("<EVENT")) {
						Pattern pEvId = Pattern.compile("eid=\"(.*?)\"");
						Matcher mEvId = pEvId.matcher(tokensWithTags.get(i));							
						if (mEvId.find()) {
							evId = mEvId.group(1);
						}
						Pattern pEvClass = Pattern.compile("class=\"(.*?)\"");
						Matcher mEvClass = pEvClass.matcher(tokensWithTags.get(i));							
						if (mEvClass.find()) {
							evClass = mEvClass.group(1);
						}
						tenseAspectPolarity = mapInstances.get(evId);
						i ++;
					} else if (tokensWithTags.get(i).contains("<TIMEX3")) {
						Pattern pTmxId = Pattern.compile("tid=\"(.*?)\"");
						Matcher mTmxId = pTmxId.matcher(tokensWithTags.get(i));							
						if (mTmxId.find()) {
							tmxId = mTmxId.group(1).replace("t", "tmx");
						}
						Pattern pTmxType = Pattern.compile("type=\"(.*?)\"");
						Matcher mTmxType = pTmxType.matcher(tokensWithTags.get(i));							
						if (mTmxType.find()) {
							tmxType = mTmxType.group(1);
						}
						Pattern pTmxValue = Pattern.compile("value=\"(.*?)\"");
						Matcher mTmxValue = pTmxValue.matcher(tokensWithTags.get(i));							
						if (mTmxValue.find()) {
							tmxValue = mTmxValue.group(1);
						}
						i ++;
					} else if (tokensWithTags.get(i).contains("<SIGNAL")) {
						Pattern pSigId = Pattern.compile("sid=\"(.*?)\"");
						Matcher mSigId = pSigId.matcher(tokensWithTags.get(i));							
						if (mSigId.find()) {
							sigId = mSigId.group(1);
						}
					} else if (tokensWithTags.get(i).contains("<C-SIGNAL")) {
						Pattern pCSigId = Pattern.compile("cid=\"(.*?)\"");
						Matcher mCSigId = pCSigId.matcher(tokensWithTags.get(i));							
						if (mCSigId.find()) {
							sigId = mCSigId.group(1).replace("c", "cs");
						}
					}
				}
			}
		}
		
		return columns;
	}
	
	public void printToConllFile(List<String> columns, String outputFilePath) throws IOException {
		FileWriter fileStream = new FileWriter(new File(outputFilePath));
		BufferedWriter out = new BufferedWriter(fileStream);
		String sent = ""; 
		int idx = 1;
		for (String s : columns) {
			if (!s.equals("")) {
				String[] cols = s.split("\t");
				if (!sent.equals(cols[2])) {
					sent = cols[2];
					idx = 1;
				}
				out.write(idx + "\t" + cols[0]);
				for (int i = 0; i < 13; i ++) {
					out.write("\t_");
				}
				out.write("\n");
			} else {
				out.write("\n");
			}
			idx ++;
		}
		out.close();
	}
	
	public void printTokenizedText(List<String> columns, String outputFilePath) throws IOException {
		FileWriter fileStream = new FileWriter(new File(outputFilePath));
		BufferedWriter out = new BufferedWriter(fileStream);
		for (String s : columns) {
			if (!s.equals("")) {
				String[] cols = s.split("\t");
				out.write(cols[0] + "\n");
			} else {
				out.write("\n");
			}
		}
		out.close();
	}
	
	public List<String> mergeColumns(List<String> timeMLCols, List<String> textProCols, List<String> mateToolsCols) {
		List<String> columns = new ArrayList<String>();
		
		for (int i=0; i<timeMLCols.size(); i++) {
			String[] tmlcols = timeMLCols.get(i).split("\t");
			String[] txpCols = textProCols.get(i).split("\t");
			String[] mateCols = mateToolsCols.get(i).split("\t");
			
			int sentIdx;
			if (!timeMLCols.get(i).equals("")) {
				if (tmlcols[2].equals("O")) sentIdx = 0;
				else sentIdx = Integer.parseInt(tmlcols[2]);
				
				String depRel = "O";
				if (sentIdx > 0) {
					int dependent = Integer.parseInt(mateCols[9]) + startOfSentences.get(sentIdx);
					depRel = "t" + dependent + ":" + mateCols[11];
				}
			
				columns.add(timeMLCols.get(i) 
						+ "\t" + txpCols[1]		// TextPro - PoS tag
						+ "\t" + txpCols[2]		// TextPro - Chunk (shallow parsing)
						+ "\t" + mateCols[3]	// Mate tools - lemma		
						+ "\t" + mateCols[5]	// Mate tools - PoS tag
						+ "\t" + depRel			// Mate tools - PoS tag
						);		
			} else {
				columns.add("");
			}
		}
		
		return columns;
	}
	
	public List<String> mergeColumns(List<String> timeMLCols, List<String> mateToolsCols) {
		List<String> columns = new ArrayList<String>();
		
		for (int i=0; i<timeMLCols.size(); i++) {
			String[] tmlcols = timeMLCols.get(i).split("\t");
			String[] mateCols = mateToolsCols.get(i).split("\t");
			
			int sentIdx;
			if (!timeMLCols.get(i).equals("")) {
				if (tmlcols[2].equals("O")) sentIdx = 0;
				else sentIdx = Integer.parseInt(tmlcols[2]);
				
				String depRel = "O";
				if (sentIdx > 0) {
					int dependent = Integer.parseInt(mateCols[9]) + startOfSentences.get(sentIdx);
					depRel = "t" + dependent + ":" + mateCols[11];
				}
			
				columns.add(timeMLCols.get(i)
						+ "\t" + mateCols[3]	// Mate tools - lemma		
						+ "\t" + mateCols[5]	// Mate tools - PoS tag
						+ "\t" + depRel			// Mate tools - PoS tag
						);		
			} else {
				columns.add("");
			}
		}
		
		return columns;
	}
	
	/**
	 * 
	 * @param includeTextPro
	 * @return
	 * @throws Exception
	 * 
	 * Converting a TimeML annotated document into a tokenized column format 
	 * (one token per line, sentence is separated by an empty line).
	 * Depending on whether TextPro output is incorporated, the resulting columns are:
	 * 
	 * - With TextPro:
	 * 		0:token				1:token-id			2:sentence-id			3:stanford-lemma   
	 * 		4:event-id			5:event-class		6:event-tense+aspect+polarity
	 * 		7:timex-id			8:timex-type		9:timex-value
	 * 		10:signal-id		11:causal-signal-id
	 * 		12:textpro-pos-tag	13:textpro-chunk
	 * 		14:mate-lemma		15:mate-pos-tag		16:mate-dep-gov
	 * 
	 * - Without TextPro:
	 * 		0:token				1:token-id			2:sentence-id			3:stanford-lemma   
	 * 		4:event-id			5:event-class		6:event-tense+aspect+polarity
	 * 		7:timex-id			8:timex-type		9:timex-value
	 * 		10:signal-id		11:causal-signal-id
	 * 		12:mate-lemma		13:mate-pos-tag		14:mate-dep-gov
	 */
	public List<String> convert(String tmlFilepath, boolean includeTextPro) throws Exception {
		List<String> finalColumns = new ArrayList<String>();
		
		// Parse TimeML document			
		List<String> columns = parse(tmlFilepath);
//		for (String s : columns) System.out.println(s);			
		
		// Print in CoNLL format as the input for the Mate tools
		printToConllFile(columns, tmlFilepath.replace(".tml", ".conll"));
				
		// Run Mate tools			
		MateToolsParser mateTools = new MateToolsParser("./tools/MateTools/");
		List<String> mateToolsColumns = mateTools.run(new File(tmlFilepath.replace(".tml", ".conll")));
//		for (String s : mateToolsColumns) System.out.println(s);
		
		if (includeTextPro) {
			// Print tokens in lines as the input for TextPro
			printTokenizedText(columns, tmlFilepath.replace(".tml", ".txt"));
			
			// Run TextPro	
			TextProParser textpro = new TextProParser("./tools/TextPro2.0/");
			String[] annotations = {"token", "pos", "chunk"};	
			List<String> textProColumns = textpro.run(annotations, new File(tmlFilepath.replace(".tml", ".txt")), true);
//			for (String s : textProColumns) System.out.println(s);	
			
			// Merge tagged columns (PoS and chunk tags from TextPro, and 
			// dependency from Mate-tools) into the TimeML annotated columns
			finalColumns = mergeColumns(columns, textProColumns, mateToolsColumns);
//			for (String s : finalColumns) System.out.println(s);
			
		} else {
			// Merge tagged columns (dependency from Mate-tools) 
			// into the TimeML annotated columns
			finalColumns = mergeColumns(columns, mateToolsColumns);
//			for (String s : finalColumns) System.out.println(s);
		}
		
		Files.delete(new File(tmlFilepath.replace(".tml", ".conll")).toPath());
		if (includeTextPro) {
			Files.delete(new File(tmlFilepath.replace(".tml", ".txt")).toPath());
		}
			
		return finalColumns;
	}
	
	public static void main(String[] args) {
		
		TimeMLToColumns tmlToCol = new TimeMLToColumns();		
		
		try {					
			List<String> columns = tmlToCol.convert("./data/example_TML/wsj_1014.tml", true);
			System.out.println(columns.get(0).split("\t").length);
			
		} catch (Exception e) {
			// Auto-generated catch block
			e.printStackTrace();
		}
		
		
		
	}

}
