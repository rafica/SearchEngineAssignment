import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Scanner;
import java.util.Set;

public class Searcher {

	private String filePath;
	private Map<String, List<String>> invertedIndex = new HashMap<String, List<String>>();
	private Map<String, Integer> wordCount = new HashMap<String, Integer>();
	private Map<String, String> docIdName = new HashMap<String, String>();
	
	private void prompt() {
		System.out.print("search> ");
	}
	private String[] textNormalization(String[] words) {
		List<String> newWords = new ArrayList<String>();
		for(String word: words) {
			/*Lower case*/
			String key = word.toLowerCase();
			
			newWords.add(key);
		}
		String[] wordArr = new String[newWords.size()];
		wordArr = newWords.toArray(wordArr);
		return wordArr;
	}
	
	
	private void storeInIndex(String[] words, String document) {
		words = textNormalization(words);
		for(String word: words){
			if(invertedIndex.containsKey(word)){
				invertedIndex.get(word).add(document);
			}
			else {
				List<String> docList = new ArrayList<String>();
				docList.add(document);
				invertedIndex.put(word, docList);
			}
		}
		wordCount.put(document, words.length);
	}
	private void buildIndex() throws FileNotFoundException, IOException  {
		BufferedReader br = new BufferedReader(new FileReader(filePath));
		String line;
		while((line = br.readLine()) != null) {
			String[] lineArray = line.split("\t", -1);
			//System.out.println(Arrays.toString(lineArray));
			if(lineArray.length > 1){
				String[] musicalGroup = lineArray[0].split("\\s+");
				//System.out.println(Arrays.toString(musicalGroup));
				storeInIndex(musicalGroup, lineArray[1]);
				docIdName.put(lineArray[1], lineArray[0]);
			}
		}
		
		
		/* writing the index to a file */
//		Writer writer = null;
//
//		try {
//		    writer = new BufferedWriter(new OutputStreamWriter(
//		          new FileOutputStream("index.txt"), "utf-8"));
//		    writer.write(Arrays.toString(invertedIndex.entrySet().toArray()));
//		} catch (IOException ex) {
//		  // report
//		} finally {
//		   try {writer.close();} catch (Exception ex) {}
//		}
		/* writing the index to a file */
		br.close();
	}
	
	private void sortDocWeights(Map<String, Double> weights) {
		Set<Entry<String, Double>> set = weights.entrySet();
        List<Entry<String, Double>> list = new ArrayList<Entry<String, Double>>(set);
        Collections.sort( list, new Comparator<Map.Entry<String, Double>>()
        {
            public int compare( Map.Entry<String, Double> o1, Map.Entry<String, Double> o2 )
            {
                return (o2.getValue()).compareTo( o1.getValue() );
            }
        } );
        for(Map.Entry<String, Double> entry:list){
            System.out.println(docIdName.get(entry.getKey())+" ==== "+entry.getValue());
        }
	}
	
	public Searcher(String filePath) throws FileNotFoundException, IOException {
		this.filePath = filePath;	
		buildIndex();		
	}

	public String search(String query) {
	
		String[] queryWords = query.split("\\s+");
		if(queryWords.length==0)
			return "Empty query";
		
		//TODO text normalization
		double Nq = queryWords.length;
		
		Map<String, Integer> queryMap = new HashMap<String, Integer>();
		Map<String, Double> docWeights = new HashMap<String, Double>();
		//Map<String, Map<String, Integer>> queryInvertedIndex = new HashMap<String, Map<String, Integer>>();
		
		for(int i=0; i< queryWords.length; i++) {
			if(queryMap.containsKey(queryWords[i])) {
				queryMap.put(queryWords[i], queryMap.get(queryWords[i])+1);
			}
			else {
				queryMap.put(queryWords[i], 1);
			}
		}
		
		for(String word: queryMap.keySet()) {
			if(!invertedIndex.containsKey(word))
				continue;
			List<String> docList = invertedIndex.get(word);
			Map<String, Integer> docMap = new HashMap<String, Integer>();
			
			for(String doc: docList) {
				if(docMap.containsKey(doc)) {
					docMap.put(doc, docMap.get(doc)+1);
				}
				else {
					docMap.put(doc, 1);
				}
			}
			//queryInvertedIndex.put(word, docMap); 
			// ^^not needed. phew :D (i think)
			int count = queryMap.get(word); //count is mostly 1 or 2 unless the user gives a query like this this this this this this this..
			for(int j = 0; j < count; j++) {
				for(String doc: docMap.keySet()) {
					if(docMap.get(doc)==0)
						continue;
					docMap.put(doc, docMap.get(doc)-1);
					if(docWeights.containsKey(doc)) {
						docWeights.put(doc, docWeights.get(doc)+1);
					}
					else {
						docWeights.put(doc, 1.0);
					}		
				}
			}
		}
		for(String doc: docWeights.keySet()) {
			double matches = docWeights.get(doc);
			double Nd = wordCount.get(doc);
			docWeights.put(doc, matches * matches / (Nq * Nd ) );
		}

		// no of matches * no. of matches /(Nd * Nq)
		
		sortDocWeights(docWeights);
		
		
		
		//boolean model
//		List<String> newQueryWords = new ArrayList<String>();
//		Boolean flag = false;
//		List<String> list1 = new ArrayList<String>();
//		List<String> list2 = new ArrayList<String>();
//		for(int i=0; i< queryWords.length; i++) {
//			if(!invertedIndex.containsKey(queryWords[i]))
//				continue;
//			if(flag) {
//				list2 = invertedIndex.get(queryWords[i]);
//				list1.retainAll(list2);
//			}
//			else{
//				flag = true;
//				list1 = new ArrayList<String>(invertedIndex.get(queryWords[i]));
//				newQueryWords.add(queryWords[i]);
//			}
//		}
//		if(list1.isEmpty())
//			return "No results";
		
		return "";
	}

	public static void main(String[] args) {
		Searcher searcher = null;
		try {
			searcher = new Searcher(args[0]);
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		catch(IOException e) {
			e.printStackTrace();
		}

		searcher.prompt();
		Scanner scanner = new Scanner(System.in);
		while(scanner.hasNext()) {
			String results = searcher.search(scanner.nextLine());
			System.out.println(results);
			searcher.prompt();
		}
		scanner.close();
	}

}
