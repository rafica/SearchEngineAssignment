import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
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

public class Searcher {

	private String filePath;
	private Map<String, Map<String, Integer>> invertedIndex = new HashMap<String, Map<String, Integer>>();
	private Map<String, Integer> wordCount = new HashMap<String, Integer>(); //needed?
	private Map<String, String> docIdName = new HashMap<String, String>();
	private int maxNumberResults = 20;
	private int editDistanceThreshold = 2;
	private Set<String> stopWords = new HashSet<String>(Arrays.asList("and", "of", "if"));
	
	private void prompt() {
		System.out.print("search> ");
	}
	private String removeAccents(String text) {
	    return text == null ? null :
	        Normalizer.normalize(text, Form.NFD)
	            .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
	}
	private boolean isStopWord(String word) { //TODO: incomplete . write code for stop word
		if(stopWords.contains(word))
			return true;
		return false;
	}
	
	private boolean isSmallWord(String word) {
		if(word.length()<=2){
			return true;
		}
		return false;
	}
	
	private String[] textNormalization(String[] words) {
		List<String> newWords = new ArrayList<String>();
		for(String word: words) {
			//removing accents and diacritics
			String word1 = removeAccents(word);
			/*Lower case*/
			String key = word1.toLowerCase();
			//TODO: handle $
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
	//TODO: third parameter as Map
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
	
	private void storeInIndex(String[] words, String document) {
		wordCount.put(document, words.length);
		words = textNormalization(words);		
		// 1-gram and bigram added here		
		for(int i=0;i<words.length; i++){
			storeFeatureInIndex(words[i], document, invertedIndex);
			if(i+1 < words.length){
				String bigram = words[i] +" "+ words[i+1];
				storeFeatureInIndex(bigram, document, invertedIndex);
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

	/*Calculates the weights for each document and populates docWeights. Returns the number of words in the query not matched*/
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
			}  //TODO: spell check for this missing word
				
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
	
//modify TODO:
	private int editDistance(String word1, String word2) {
		int len1 = word1.length();
		int len2 = word2.length();
	 
		// len1+1, len2+1, because finally return dp[len1][len2]
		int[][] dp = new int[len1 + 1][len2 + 1];
	 
		for (int i = 0; i <= len1; i++) {
			dp[i][0] = i;
		}
	 
		for (int j = 0; j <= len2; j++) {
			dp[0][j] = j;
		}
	 
		//iterate though, and check last char
		for (int i = 0; i < len1; i++) {
			char c1 = word1.charAt(i);
			for (int j = 0; j < len2; j++) {
				char c2 = word2.charAt(j);
	 
				//if last two chars equal
				if (c1 == c2) {
					//update dp value for +1 length
					dp[i + 1][j + 1] = dp[i][j];
				} else {
					int replace = dp[i][j] + 1;
					int insert = dp[i][j + 1] + 1;
					int delete = dp[i + 1][j] + 1;
	 
					int min = replace > insert ? insert : replace;
					min = delete > min ? min : delete;
					dp[i + 1][j + 1] = min;
				}
			}
		}
	 
		return dp[len1][len2];
	}
	
	private Map<String, Integer> findClosestWords(String word, Map<String, Map<String, Integer>> index) {
		Map<String, Integer> results = new HashMap<String, Integer>(); // word and its edit distance
		int localThreshold = editDistanceThreshold;
		//if results are empty try increasing the threshold and try again.
		
		//if its a stop word, dont search in the index
		if(isStopWord(word) || isSmallWord(word)){
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
	
	public Searcher(String filePath) throws FileNotFoundException, IOException {
		this.filePath = filePath;	

		buildIndex();		
	}
	
	/* finds the best closest query to the incorrect input query and returns its corresponding docWeights*/
	private Map<String, Integer> relaxedSearch(String[] queryWords, Map<String, Map<String, Integer>> index, Boolean notPerfect) {
		// To find the right combination of query words which has the highest no. of matches.
		
		//first get all possible combinations
		Map<List<String>, Integer> possibleQueries = getAllPossibleQueries(queryWords, index);
		
		//find the query whose first result has the maximum matches.
		//make queryMap for these queries
		return findBestQuery(possibleQueries, index, notPerfect);
	}
	
	/* finding the queries which have the maximum matches and return the docWeight */
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
			if(weight == maxWeight) {
				bestQueries.add(query);
				mergeMap(bestWeights, docWeights); 
			}
			else if(weight > maxWeight) {
				bestQueries.clear();
				bestWeights.clear();
				bestQueries.add(query);
				mergeMap(bestWeights, docWeights);  //dont merge here
				maxWeight = weight;
			}
		}
		// sort these best queries based on edit distance in the search function TODO:
		// take top 5 (?)
		
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
			}
		}
		
		return bestWeights;
		
	}
	private void mergeMap(Map<String, Integer> bestWeights, Map<String, Integer> docWeights) {
		for(String doc: docWeights.keySet()) {
			if(!bestWeights.containsKey(doc) ||docWeights.get(doc) > bestWeights.get(doc) ) {
				bestWeights.put(doc, docWeights.get(doc));
			}
		}
	} 
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
	
	//finds all possible queries and their edit distance
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
	
	private void rankingDocs(List<List<Entry<String, Integer>>> topRanked, Map<String, Integer> docWeights) {
		List<Entry<String, Integer>> sorted = sortWeight(docWeights);	
		Integer first = null;
		
		int i=0;
		// SECOND LEVEL OF RANKING
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
	}
	
	public String search(String query) throws IOException {
		String[] queryWords = query.split("\\s+");
		if(query.isEmpty() || queryWords.length==0)
			return "Empty query";

		queryWords = textNormalization(queryWords);
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
		relaxedDocWeights = relaxedSearch(queryWords, invertedIndex, isPerfect);
		mergeMap(docWeights, relaxedDocWeights); 
		
//		if(notMatches == queryWords.length) { // no words match
//			//brute force
//			docWeights = relaxedSearch(queryWords, invertedIndex);
//		}
//		if(notMatches != 0) {
			//get the keys of docWeights.
			//build index for these documents TODO: please dont use these results. remove stop words and try again (exception if the matched word is a stop word)
			//call searchRelaxed and perform the same algorithm with a relaxed condition using edit distance.
			
//			Set<String> partialResults = docWeights.keySet();
//			Map<String, Map<String, Integer>> partialIndex = new HashMap<String, Map<String, Integer>>();
//			buildPartialIndex(partialIndex, partialResults);
//			docWeights = relaxedSearch(queryWords, partialIndex);
//			
//			docWeights = relaxedSearch(queryWords, invertedIndex);
//		}
		
		if(docWeights.isEmpty())
			return "No results found";
		List<List<Entry<String, Integer>>> topRanked = new ArrayList<List<Entry<String, Integer>>>();
		rankingDocs(topRanked, docWeights);
		
       
        int count = 0;
        BufferedReader console = new BufferedReader( new InputStreamReader(System.in));
		String input;
		for(List<Entry<String, Integer>> list: topRanked) {
			for(Map.Entry<String, Integer> entry:list){
				if(count == 0 && isPerfect) {
					System.out.println("Best match : "+ docIdName.get(entry.getKey()) + "  "+ entry.getKey());
					System.out.println("Closest matches : ");
					count++;
					continue;
				}
				else if(count==0){
					System.out.println("Closest matches : ");
					System.out.println(docIdName.get(entry.getKey()) + "  "+ entry.getKey());
					count++;
					continue;
				}
					
				
				System.out.println(docIdName.get(entry.getKey()) + "  "+ entry.getKey());
				count++;
				
				if(count == maxNumberResults+1){
					System.out.println("Show more results? (Y/y)");
					count = 1;
					input = console.readLine();
					if(input.equals("y") || input.equals("Y")) {
						continue;
					} 
					else {
						return "";
					}
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
//        int i =0;
//        for(Map.Entry<String, Integer> entry:list){
//        	i = i+1;
//        	if(i>30){
//        		break;
//        	}
//            System.out.println(docIdName.get(entry.getKey())+" ==== "+entry.getValue());
//        }
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
