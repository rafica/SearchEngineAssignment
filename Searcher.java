import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Writer;
import java.text.Normalizer;
import java.text.Normalizer.Form;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

/**
 * 
 * @author rafica
 * 
 */
public class Searcher {
	
	private String filePath;
	private Map<String, Map<String, Integer>> invertedIndex = new HashMap<String, Map<String, Integer>>();
	private Map<String, Integer> wordCount = new HashMap<String, Integer>(); 
	private Map<String, String> docIdName = new HashMap<String, String>();
	private int maxNumberResults = 20;
	private int editDistanceThreshold = 2;
	private Set<String> stopWords = new HashSet<String>(Arrays.asList("and", "of", "if", "a", "are"));
	public boolean isFileOutput = false;    //if isFileOutput is set to True, the best match output IDs are written to a file
	
	private void prompt() {
		System.out.print("search> ");
	}
	
	/**
	 * Removing accents 
	 * @param text
	 * @return String
	 */
	private String removeAccents(String text) {
	    return text == null ? null :
	        Normalizer.normalize(text, Form.NFD)
	            .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
	}
	
	/**
	 * Check if the given word is stop word
	 * @param word
	 * @return boolean
	 */
	private boolean isStopWord(String word) { 
		if(stopWords.contains(word))
			return true;
		return false;
	}
	
	/**
	 * Check if the given word is a small word
	 * @param word
	 * @return
	 */
	private boolean isSmallWord(String word) {
		if(word.length()<=3){
			return true;
		}
		return false;
	}

	/**
	 * Text pre-processing
	 * @param words - Array of words to be processed
	 * @param flag - false if the processing is on query, true otherwise
	 * @return processed words array
	 */
	private String[] textNormalization(String[] words, Boolean flag) {
		List<String> newWords = new ArrayList<String>();
		for(String word: words) {
			//removing accents and diacritics
			String word1 = removeAccents(word);
			/*Lower case*/
			String key = word1.toLowerCase();
			//remove [],{} () " 
			String removedChar = key.replaceAll("[,\"%\\[\\{\\]\\}()]+", "");
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
					if(flag){
						String second = feature.replaceAll("\\.", " ");
						Collections.addAll(finalFeatures, second.split("\\s+"));
					}
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
			
			newWords.addAll(finalFeatures);
		}
		String[] wordArr = new String[newWords.size()];
		wordArr = newWords.toArray(wordArr);
		return wordArr;
	}
	
	/**
	 * Puts the feature as the first parameter into the HashMap passed as second parameter
	 * @param feature
	 * @param document
	 * @param index
	 */
	private void storeFeatureInIndex(String feature, String document, Map<String, Map<String, Integer>> index) {
		if(index.containsKey(feature)){
			Map<String,Integer> m =	index.get(feature);
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
			index.put(feature, docMap);
		}	
	}

	/**
	 * Gets words and bigrams from the array of strings passed and puts in the index with document as key 
	 * @param words
	 * @param document
	 */
	private void storeInIndex(String[] words, String document) {
		wordCount.put(document, words.length);
		words = textNormalization(words, true);		
		// 1-gram and bigram added here		
		for(int i=0;i<words.length; i++){
			storeFeatureInIndex(words[i], document, invertedIndex);
			if(i+1 < words.length){
				String bigram = words[i] +" "+ words[i+1];
				storeFeatureInIndex(bigram, document, invertedIndex);
			}		
		}	
	}
	
	/**
	 * Reads the documents from the input file path
	 * @throws FileNotFoundException
	 * @throws IOException
	 */
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

	/**
	 * writes the index to a file
	 */
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
	

	/**
	 * Stores the features from the query into the queryMap
	 * @param queryWords
	 * @param queryMap
	 */
	private void addFeaturesToQueryIndex(String[] queryWords, Map<String, Integer> queryMap) {
		for(int i=0; i< queryWords.length; i++) {
			if(queryMap.containsKey(queryWords[i])) {
				queryMap.put(queryWords[i], queryMap.get(queryWords[i])+1);
			}
			else {
				queryMap.put(queryWords[i], 1);
			}
			if(i < queryWords.length-1){
				String bigram = queryWords[i] + " " +queryWords[i+1];
				if(queryMap.containsKey(bigram)) {
					queryMap.put(bigram, queryMap.get(bigram)+1);
				}
				else {
					queryMap.put(bigram, 1);
				}
			}
		}
	}

	/**
	 * Calculates the weights for each document and populates docWeights.
	 * @param queryMap
	 * @param docWeights
	 * @param index
	 * @return the number of words in the query not matched
	 */
	private Integer findWeights(Map<String, Integer> queryMap, Map<String, Integer> docWeights, Map<String, Map<String, Integer>> index) {
		Integer notMatched = 0;
		for(String word: queryMap.keySet()) {
			if(word.isEmpty()) {
				continue;
			}
			if(!index.containsKey(word)){
				//make sure the word is not a bigram
				if(word.split("\\s+").length<2){
					notMatched = notMatched + queryMap.get(word);
				}
				continue;
			}
				
			Map<String, Integer> docMap = new HashMap<String, Integer>(index.get(word));	
			
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
		return notMatched;
	}

	/**
	 * Finds the minimum edit distance between two words 
	 * @param word1
	 * @param word2
	 * @return integer
	 */
	private int editDistance(String word1, String word2) {
		int len1 = word1.length();
		int len2 = word2.length();
	 
		int[][] dp = new int[len1 + 1][len2 + 1];
	 
		for(int i=0; i<=len1; i++){
			dp[i][0] = i;
		}
	 
		for(int j=0;j<=len2;j++) {
			dp[0][j] = j;
		}
	 
		for(int i = 0; i < len1; i++) {
			char c1 = word1.charAt(i);
			for(int j = 0; j < len2; j++) {
				char c2 = word2.charAt(j);
	 
				//if last two chars equal
				if (c1 == c2) {
					dp[i+1][j+1] = dp[i][j];
				}
				else {
					int replace = dp[i][j]+1;
					int insert = dp[i][j+1]+1;
					int delete = dp[i+1][j]+1;
	 
					int min = replace > insert ? insert : replace;
					min = delete > min ? min : delete;
					dp[i+1][j+1] = min;
				}
			}
		}
	 
		return dp[len1][len2];
	}

	/**
	 * For the given word, find the closest words with edit distance threshold of 1. If none are found, the threshold is increased.
	 * @param word
	 * @param index
	 * @return a map with the word and its edit distance
	 */
	private Map<String, Integer> findClosestWords(String word, Map<String, Map<String, Integer>> index) {
		Map<String, Integer> results = new HashMap<String, Integer>(); // word and its edit distance
		int localThreshold = editDistanceThreshold;
		//if results are empty try increasing the threshold and try again.
		
		//if present in index, dont search for alternatives
		if(index.containsKey(word)){
			results.put(word, 0);
		}
		//if its a stop word, dont search in the index
		else if(isStopWord(word) || isSmallWord(word)){
			results.put(word, 0);
		}
			
		while(results.isEmpty()){
			for(String key: index.keySet()) {
				int distance = editDistance(key, word);

				if(distance <= localThreshold){
					if(isStopWord(key) && key.length() <= word.length()){  // if its close to a stop word, use that
						results.clear();
						results.put(key, distance);
						break;
					}
					results.put(key, distance);
				}
			}
			localThreshold++;
		}
		
		return results;
	}
	
	/**
	 * Constructor which calls buildIndex
	 * @param filePath
	 * @throws FileNotFoundException
	 * @throws IOException
	 */
	public Searcher(String filePath) throws FileNotFoundException, IOException {
		this.filePath = filePath;	

		buildIndex();		
	}
	
	/**
	 * finds the best closest query to the incorrect input query
	 * @param queryWords
	 * @param index
	 * @param notPerfect
	 * @return merged docWeights map
	 */
	private Map<String, Integer> relaxedSearch(String[] queryWords, Map<String, Map<String, Integer>> index, Boolean notPerfect) {
	
		//first get all possible combinations
		Map<List<String>, Integer> possibleQueries = getAllPossibleQueries(queryWords, index);
		
		//find the query whose first result has the maximum matches.
		return findBestQuery(possibleQueries, index, notPerfect);
	}
	

	/**
	 * finds the queries which have the maximum matches and returns 
	 * @param possibleQueries
	 * @param partialIndex
	 * @param isPerfect
	 * @return the merged docWeights 
	 */
	private Map<String, Integer> findBestQuery(Map<List<String>, Integer> possibleQueries, Map<String, Map<String, Integer>> partialIndex, Boolean isPerfect) {
		
		int maxWeight = 0;
		List<List<String>> bestQueries = new ArrayList<List<String>>();
		Map<String, Integer> bestWeights = new HashMap<String, Integer>();
		for(List<String> query: possibleQueries.keySet()) {
			Map<String, Integer> docWeights = new HashMap<String, Integer>();
			Map<String, Integer> queryMap = new HashMap<String, Integer>();
			
			String[] queryWords = new String[query.size()];
			queryWords = query.toArray(queryWords);
			
			addPartialFeaturesToQueryIndex(queryWords, queryMap);
			
			findWeights(queryMap, docWeights, partialIndex);
			int weight = findMaxWeight(docWeights);
				// if a query has maximum weight add its docweights to the existing bestWeights map.
			if(weight == maxWeight) {
				bestQueries.add(query);
				mergeMap(bestWeights, docWeights); 
			}
				//update the maxWeight and clear the existing best queries and best weights map
			else if(weight > maxWeight) {
				bestQueries.clear();
				bestWeights.clear();
				bestQueries.add(query);
				mergeMap(bestWeights, docWeights); 
				maxWeight = weight;
			}
		}
		
		int count = 10;
		if(!isPerfect){
			System.out.println("The query has mistakes. Showing results for the following queries");	
			for(List<String> query: bestQueries) {
				StringBuilder sb = new StringBuilder();
				
				for(int i=0, il = query.size(); i < il; i++){
					if(i>0)
						sb.append(" ");
					sb.append(query.get(i));
				}
				System.out.println("\t"+sb.toString());
				count--;
				if(count==0)
					break;
			}
		}
		
		return bestWeights;
		
	}

	/**
	 * Merges two doc weights map
	 * @param bestWeights
	 * @param docWeights
	 */
	private void mergeMap(Map<String, Integer> bestWeights, Map<String, Integer> docWeights) {
		for(String doc: docWeights.keySet()) {
			if(!bestWeights.containsKey(doc) ||docWeights.get(doc) > bestWeights.get(doc) ) {
				bestWeights.put(doc, docWeights.get(doc));
			}
		}
	} 
	

	/**
	 * Find the maximum number of matches in the docWeights map 
	 * @param docWeights
	 * @return
	 */
	private Integer findMaxWeight(Map<String, Integer> docWeights) {
		int max = 0;
		for(String key: docWeights.keySet()) {
			int matches = docWeights.get(key); 
			if(matches > max) {
				max = matches;
			}
		}
		return max;
	}	

	/**
	 *  A Recursive function which finds all possible combinations of queries. 
	 *  Input is : possibleWords - For queries of n words there are n lists with each list having all closest words for that word
	 * @param possibleWords
	 * @param listIndex
	 * @param buffer
	 * @param editDistance
	 * @param result
	 * @return a map of all possible queries and their edit distances
	 */
	private Map<List<String>, Integer> possibleCombinations(List<Map<String, Integer>> possibleWords, int listIndex, List<String> buffer, Integer editDistance, Map<List<String>,Integer> result) {
		if(listIndex==possibleWords.size()){
			List<String> copy = new ArrayList<String>(buffer);
			result.put(copy, editDistance);
			return result;
		}
		
		for(String word: possibleWords.get(listIndex).keySet()) {
			buffer.add(word); //add the word
			editDistance = editDistance + possibleWords.get(listIndex).get(word); // add its edit distance
			possibleCombinations(possibleWords, listIndex+1, buffer, editDistance, result);
			buffer.remove(buffer.size()-1);
			editDistance = editDistance - possibleWords.get(listIndex).get(word);
		}
		return result;
	}
	

	/**
	 * finds all possible queries for the given input query words
	 * @param queryWords
	 * @param partialIndex
	 * @return map of all possible queries and their edit distances
	 */
	private Map<List<String>, Integer> getAllPossibleQueries(String[] queryWords, Map<String, Map<String, Integer>> partialIndex) {
		List<Map<String, Integer>> possibleWords = new ArrayList<Map<String, Integer>>(); // not the result!
		
		for(String word: queryWords) {
			//get closest matches 
			Map<String, Integer> closestMatches = findClosestWords(word, partialIndex);
			possibleWords.add(closestMatches);
		}
		List<String> buffer = new ArrayList<String>();
		Map<List<String>, Integer> result = new HashMap<List<String>, Integer>();
		
		return possibleCombinations(possibleWords, 0, buffer, 0, result);
		
	}
	
	/**
	 * Adding only single words to the query map 
	 * @param queryWords
	 * @param queryMap
	 */
	private void addPartialFeaturesToQueryIndex(String[] queryWords, Map<String, Integer> queryMap ) {
		for(int i=0; i< queryWords.length; i++) {
			if(queryMap.containsKey(queryWords[i])) {
				queryMap.put(queryWords[i], queryMap.get(queryWords[i])+1);
			}
			else {
				queryMap.put(queryWords[i], 1);
			}
		}
	}

	/**
	 * Ranks the documents based on docWeights (input) 
	 * @param docWeights
	 * @return list of top ranked documents
	 */
	private List<List<Entry<String, Integer>>> rankingDocs(Map<String, Integer> docWeights) {
		List<List<Entry<String, Integer>>> topRanked = new ArrayList<List<Entry<String, Integer>>>();
		
		// Sorting based on the number of matches in each document
		List<Entry<String, Integer>> sorted = sortWeight(docWeights);	
		Integer first = null;
		
		int i=0;
		// second level of ranking. Sorting based on word length + number of characters
		Map<String, Integer> map = new HashMap<String, Integer>();
		for(Map.Entry<String, Integer> entry: Reversed.reversed(sorted)){
			if(i==0){
				first = entry.getValue();
				// using number of words+character count as the weight 
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
		return topRanked;
	}
	/**
	 * Search is performed, documents are ranked and the ID for the best match is returned
	 * @param query
	 * @return the ID of the best match
	 * @throws IOException
	 */
	public String search(String query) throws IOException{
		String[] queryWords = query.split("\\s+");
		if(query.isEmpty() || queryWords.length==0){
			System.out.println("Empty query");
			return "";
		}

		queryWords = textNormalization(queryWords, false);  //false represents normalization is done on the query
		Map<String, Integer> queryMap = new HashMap<String, Integer>();
		Map<String, Integer> docWeights = new HashMap<String, Integer>();
		Map<String, Integer> relaxedDocWeights = new HashMap<String, Integer>();
		
		//Adding words and bigrams
		addFeaturesToQueryIndex(queryWords, queryMap);
		
		//Finding weights for documents
		Boolean isPerfect = true;
		Integer notMatches = findWeights(queryMap, docWeights, invertedIndex);
		if( notMatches!=0 ){
			isPerfect = false;
		}
		if(!isPerfect){
			relaxedDocWeights = relaxedSearch(queryWords, invertedIndex, isPerfect);
			mergeMap(docWeights, relaxedDocWeights);
		}
	
		if(docWeights.isEmpty()){
			System.out.println("No results found");
			return "";
		}
		List<List<Entry<String, Integer>>> topRanked = rankingDocs(docWeights);
				
        String result = "";
        int count = 0;
        BufferedReader console = new BufferedReader( new InputStreamReader(System.in));
		String input;
		for(List<Entry<String, Integer>> list: topRanked) {
			for(Map.Entry<String, Integer> entry:list){
				if(count == 0 && isPerfect) {
					if(!isFileOutput){
						System.out.println("++++++++++++++++++++++++++++++++++++++++++++++++++++");
						System.out.println();
						System.out.println("BEST MATCH : "+ docIdName.get(entry.getKey()) + " ==== "+ entry.getKey());
						System.out.println("**********");
						System.out.println("-------------------------------------------------");
						System.out.println("CLOSEST MATCHES : ");
						System.out.println("***************");
					}
					result = entry.getKey();
					count++;
					continue;
				}
				else if(count==0){
					if(!isFileOutput) {
						System.out.println("-------------------------------------------------");
						System.out.println("CLOSEST MATCHES : ");
						System.out.println("***************");
						System.out.println(docIdName.get(entry.getKey()) + " ==== "+ entry.getKey());
					}
					count++;
					result = entry.getKey();
					continue;
				}
				if(!isFileOutput)				
					System.out.println(docIdName.get(entry.getKey()) + " ==== "+ entry.getKey());
				count++;
				
				if(count == maxNumberResults+1){
					if(isFileOutput)
						break;
					System.out.println("-------------------------------------------------");
					System.out.println("Show more results? (Y/y)");
					count = 1;
					input = console.readLine();
					if(input.equals("y") || input.equals("Y")) {
						continue;
					} 
					else {																	  
						System.out.println("++++++++++++++++++++++++++++++++++++++++++++++++++++");
						return "";
					}
				}			
			}
			if(isFileOutput)
				break;
		}
		System.out.println("-------------------------------------------------");
		System.out.println("No more results");
		return result; 
	}

	/**
	 * Sorts a map in ascending order based on the values
	 * @param weights - a map
	 * @return a sorted map with data type list<Entry<string, integer>>
	 */
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
        return list;
	}
	
	public static void main(String[] args){
		Searcher searcher = null;
		try {
			searcher = new Searcher(args[0]);
			searcher.prompt();
			BufferedReader buf = new BufferedReader (new InputStreamReader (System.in));
			String input = buf.readLine ();

			//isFileOutput determines if the output should be in a file
			searcher.isFileOutput = true;	

			//		File fout = new File("out.txt");
			//		FileOutputStream fos = new FileOutputStream(fout);
			//		BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(fos));

			PrintWriter writer = new PrintWriter("output.txt", "UTF-8");
			
			while(input!=null) {
				String results = searcher.search(input);
				if(searcher.isFileOutput){		
					//				bw.write(results);  // TODO: file is not writing.???
					//				bw.newLine();
					writer.println(results);
				}			
				System.out.println(results);
				searcher.prompt();
				input = buf.readLine();
			}
			buf.close();
			writer.close();
			
		} catch (FileNotFoundException e) {
			System.out.println("file not found");
			e.printStackTrace();
		}
		catch(IOException e) {
			e.printStackTrace();
		}

	}

}
