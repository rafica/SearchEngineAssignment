import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

public class Searcher {

	private String filePath;
	private Map<String, Map<String, Integer>> invertedIndex = new HashMap<String, Map<String, Integer>>();
	private Map<String, Integer> wordCount = new HashMap<String, Integer>(); //needed?
	private Map<String, String> docIdName = new HashMap<String, String>();
	
	private void prompt() {
		System.out.print("search> ");
	}
	private String[] textNormalization(String[] words) {
		List<String> newWords = new ArrayList<String>();
		for(String word: words) {
			/*Lower case*/
			String key = word.toLowerCase();

			//remove [],{} () " 
			String removedChar = key.replaceAll("[,\"\\[\\{\\]\\}()]+", "");
			// replace & with and
			String removedChar1 = removedChar.replaceAll("&", "and");
			// making ? and ! as separate words
			String removedChar2 = removedChar1.replaceAll("\\?", " ?");
			String removedChar3 = removedChar2.replaceAll("!", " !");
		
			//replace - + : ; > < \ / with " " 
			String removedChar4 = removedChar3.replaceAll("[+/:><;\\\\-]+", " ");
				
			String[] changedFeatures = removedChar4.split("\\s+");
			List<String> finalFeatures = new ArrayList<String>();
			
			for(String feature: changedFeatures) {
				//replacing with and without space if a word contains . or '
				// if it doesnt contain any special character, just add the word
				if(feature.contains(".")){
					String first = feature.replaceAll("\\.", "");
					finalFeatures.add(first);
					String second = feature.replaceAll("\\.", " ");
					Collections.addAll(finalFeatures, second.split("\\s+"));
				}
				else if(feature.contains("'")){
					String first = feature.replaceAll("'", "");
					finalFeatures.add(first);
					String second = feature.replaceAll("'", " ");
					Collections.addAll(finalFeatures, second.split("\\s+"));
				}
				else {
					finalFeatures.add(feature);
				}
			}

			//replace empty with <<<no name>>> ??? TODO:
			
			newWords.addAll(finalFeatures);
		}
		String[] wordArr = new String[newWords.size()];
		wordArr = newWords.toArray(wordArr);
		return wordArr;
	}
	
	private void storeFeatureInIndex(String feature, String document) {
		if(invertedIndex.containsKey(feature)){
			Map<String,Integer> m =	invertedIndex.get(feature);
			if(m.containsKey(document)){
				m.put(document, m.get(document)+1 );
			}
			else {
				m.put(document, 1);
			}
		}
		else {
			Map<String, Integer> docMap = new HashMap<String, Integer>();
			docMap.put(document, 1);
			invertedIndex.put(feature, docMap);
		}	
	}
	
	private void storeInIndex(String[] words, String document) {
		wordCount.put(document, words.length);
		words = textNormalization(words);		
		// 1-gram and bigram added here		
		for(int i=0;i<words.length; i++){
			storeFeatureInIndex(words[i], document);
			if(i+1 < words.length){
				String bigram = words[i] +" "+ words[i+1];
				storeFeatureInIndex(bigram, document);
			}		
		}	
	}
	
	private void buildIndex() throws FileNotFoundException, IOException  {
		BufferedReader br = new BufferedReader(new FileReader(filePath));
		String line;
		while((line = br.readLine()) != null) {
			String[] lineArray = line.split("\t", -1);			
			if(lineArray.length > 1){
				String[] musicalGroup = lineArray[0].split("\\s+");
				storeInIndex(musicalGroup, lineArray[1]);
				docIdName.put(lineArray[1], lineArray[0]);
			}
		}
		//writeIndexToFile();
		br.close();
	}
	
	@SuppressWarnings("unused")
	private void writeIndexToFile() {
		Writer writer = null;
		try {
		    writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream("index.txt"), "utf-8"));
		    writer.write(Arrays.toString(invertedIndex.entrySet().toArray()));
		} catch (IOException ex) {
		  // report
		} finally {
		   try {writer.close();} catch (Exception ex) {}
		}
	}
	

	
	private void addFeaturesToQueryIndex(String[] queryWords, Map<String, Integer> queryMap) {
		for(int i=0; i< queryWords.length; i++) {
			if(queryMap.containsKey(queryWords[i])) {
				queryMap.put(queryWords[i], queryMap.get(queryWords[i])+1);
			}
			else {
				queryMap.put(queryWords[i], 1);
			}
			if(i < queryWords.length-1){
				String bigram = queryWords[i] + queryWords[i+1];
				if(queryMap.containsKey(bigram)) {
					queryMap.put(bigram, queryMap.get(bigram)+1);
				}
				else {
					queryMap.put(bigram, 1);
				}
			}
		}
	}
	
	private void findWeights(Map<String, Integer> queryMap, Map<String, Integer> docWeights) {
		for(String word: queryMap.keySet()) {
			if(!invertedIndex.containsKey(word))  //TODO: spell check for this missing word
				continue;
			Map<String, Integer> docMap = new HashMap<String, Integer>(invertedIndex.get(word));		
			int count = queryMap.get(word); 
			for(int j = 0; j < count; j++) {
				for(String doc: docMap.keySet()) {
					if(docMap.get(doc)==0)
						continue;
					docMap.put(doc, docMap.get(doc)-1);
					if(docWeights.containsKey(doc)) {
						docWeights.put(doc, docWeights.get(doc)+1);
					}
					else {
						docWeights.put(doc, 1);
					}		
				}
			}
		}
	}
	

	public Searcher(String filePath) throws FileNotFoundException, IOException {
		this.filePath = filePath;	
		buildIndex();		
	}

	public String search(String query) throws IOException {
		String[] queryWords = query.split("\\s+");
		if(query.isEmpty() || queryWords.length==0)
			return "Empty query";

		queryWords = textNormalization(queryWords);
		Map<String, Integer> queryMap = new HashMap<String, Integer>();
		Map<String, Integer> docWeights = new HashMap<String, Integer>();
		
		//Adding words and bigrams
		addFeaturesToQueryIndex(queryWords, queryMap);
		
		//Finding weights for documents
		findWeights(queryMap, docWeights);
			
		if(docWeights.isEmpty())
			return "No results found";
	
		List<Entry<String, Integer>> sorted = sortWeight(docWeights);
		List<List<Entry<String, Integer>>> topRanked = new ArrayList<List<Entry<String, Integer>>>();
		Integer first = null;
		
		int i=0;
		// SECOND LEVEL OF RANKING
		System.out.println("SECOND LEVEL OF SORTING");
		Map<String, Integer> map = new HashMap<String, Integer>();
		for(Map.Entry<String, Integer> entry: Reversed.reversed(sorted)){
			if(i==0){
				first = entry.getValue();
				// using number of words+character count as the weight (check testcase1)
				map.put(entry.getKey(), wordCount.get(entry.getKey()) + docIdName.get(entry.getKey()).length());
				i=1;
			}
			else if(first.equals(entry.getValue()))
				map.put(entry.getKey(), wordCount.get(entry.getKey()) + docIdName.get(entry.getKey()).length());
			else {
				topRanked.add(sortWeight(map));
				map = new HashMap<String, Integer>();
				first = entry.getValue();
				map.put(entry.getKey(), wordCount.get(entry.getKey()) + docIdName.get(entry.getKey()).length());
			}
		}	
		topRanked.add(sortWeight(map)); 
		
        System.out.println("(Q or q) to quit\n");
        
        int maxCount = 20; // can change
        int count = 0;
        BufferedReader console = new BufferedReader( new InputStreamReader(System.in));
		String input;
		for(List<Entry<String, Integer>> list: topRanked) {
			for(Map.Entry<String, Integer> entry:list){
				System.out.println("Did you mean "+ docIdName.get(entry.getKey()) + "  "+ entry.getKey() +" ? (Y or y)");
				input = console.readLine();

				if(input.equals("y") || input.equals("Y")) {
					return "ID: "+entry.getKey();
				} 
				else if(input.equals("q") || input.equals("Q")) {
					return "";
				}
				else {
					count++;
					if(count>= maxCount)
						return "No more results";
					continue;
				}
			}
		}
 	
		return "No more results"; 
	}

	private List<Entry<String, Integer>> sortWeight(Map<String, Integer> weights) {
		Set<Entry<String, Integer>> set = weights.entrySet();
		List<Entry<String, Integer>> list = new ArrayList<Entry<String, Integer>>(set);
        Collections.sort( list, new Comparator<Map.Entry<String, Integer>>()
        {
            public int compare( Map.Entry<String, Integer> o1, Map.Entry<String, Integer> o2 )
            {
                return (o1.getValue()).compareTo( o2.getValue() );
            }
        } );

        //TODO: remove prints
        int i =0;
        for(Map.Entry<String, Integer> entry:list){
        	i = i+1;
        	if(i>30){
        		break;
        	}
            System.out.println(docIdName.get(entry.getKey())+" ==== "+entry.getValue());
        }
        return list;
	}
	
	public static void main(String[] args) throws IOException {
		
		//TODO: handle the exceptions 
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
		//Scanner scanner = new Scanner(System.in);
		BufferedReader buf = new BufferedReader (new InputStreamReader (System.in));
		String input = buf.readLine ();

		while(input!=null) {
			String results = searcher.search(input);
			System.out.println(results);
			searcher.prompt();
			input = buf.readLine();
		}
		buf.close();
	}

}
