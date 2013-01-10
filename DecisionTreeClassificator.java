package implementation.classifiers;

import java.io.FileOutputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPOutputStream;

import org.apache.log4j.Logger;

import exceptions.ClassificationSystemException;

import implementation.model.DocumentType;
import implementation.model.Website;
import implementation.model.WebsitesCollection;
import implementation.utils.Predicate;
import implementation.utils.Predicates;
import interfaces.ClassificationResult;
import interfaces.ClassificatorInterface;
import interfaces.SiteInterface;

/**
 * Implementacja Klasyfikatora Drzewa Decyzyjnego
 * @author	
 *
 */
public class DecisionTreeClassificator implements ClassificatorInterface, Serializable {

	private static final long serialVersionUID = 1L;

	private static Logger log = Logger.getLogger(DecisionTreeClassificator.class);
	private transient final static Predicate[] collectionFilters = new Predicate[]{
		//Predicates.notSingleWord,
		Predicates.notSingleDocument
	};
	private transient final static Predicate[] websiteFilters = new Predicate[]{
			Predicates.notShortWord,
			Predicates.notNumber
	};

	private WebsitesCollection positive;
	private WebsitesCollection negative;
	private DocumentType documentType;

	/**
	 * Średnie prawdopodobieństwo przynależności do klasy
	 */
	public final double inClassProbability = 0.5;

	/**
	 * @param	parameters parametry (format danych)
	 */
	protected void configure(DecisionTreeClassificatorParameters parameters){

		documentType = parameters.getFormat();
	}

	@Override
	public Boolean teach(Map<SiteInterface, Boolean> learningSet) {
		log.info("[uczenie] zbior uczacy: " + learningSet.size() + " elementow");
		List<SiteInterface> positiveSites = new ArrayList<SiteInterface>();
		List<SiteInterface> negativeSites = new ArrayList<SiteInterface>();
		for(Map.Entry<SiteInterface, Boolean> site : learningSet.entrySet())
			if (site.getValue())
				positiveSites.add(site.getKey());
			else
				negativeSites.add(site.getKey());
		setPositive(new WebsitesCollection(positiveSites));
		getPositive().setFilters(websiteFilters);
		getPositive().makeTermsMap(collectionFilters);

		setNegative(new WebsitesCollection(negativeSites));
		getNegative().setFilters(websiteFilters);
		getNegative().makeTermsMap(collectionFilters);

		return null;
	}

	@Override
	public ClassificationResult classify(SiteInterface site) {
		//log.debug("[klasyfikacja] strona: " + site.getUrl());
		switch(documentType){
		case BINARY:
			return classifyBinary(site);
		case TF:
			return classifyTf(site);
		case MIXED:
			return classifyMixedBinaryTf(site);
		}
		return null;
	}

	@Override
	public List<ClassificationResult> classify(List<SiteInterface> sites) {
		log.info("[klasyfikacja] zbior: " + sites.size() + " stron");
		List<ClassificationResult> result = new ArrayList<ClassificationResult>();
		for(SiteInterface site : sites)
			// TODO wielowatkowowsc
			result.add(classify(site));
		return result;
	}

	private BigDecimal[] computeBinaryProbabilities(SiteInterface site){
		Website website = new Website(site.getUrl(), true);
		website.makeTermsMap(websiteFilters);
		Set<String> words = website.getTerms().keySet();
		BigDecimal positiveProbability = BigDecimal.valueOf(inClassProbability);
		BigDecimal negativeProbability = BigDecimal.valueOf(1.0 - inClassProbability);
		log.debug("[classifyBinary] liczenie prawdopodobienstw [BEGIN]");
		
		double 	positiveDocumentCount = getPositive().getDocumentCount() + 2.0,
				negativeDocumentCount = getNegative().getDocumentCount() + 2.0;
		
		for(String word : words){
			positiveProbability = positiveProbability.multiply(
				BigDecimal.valueOf(
					(getPositive().countDocumentsWithTerm(word) + 1.0) / positiveDocumentCount
				)
			);
			negativeProbability = negativeProbability.multiply(
				BigDecimal.valueOf(
					(getNegative().countDocumentsWithTerm(word) + 1.0) / negativeDocumentCount
				)
			);
		}

		log.debug("[classifyBinary] liczenie prawdopodobienstw [END]");
		return new BigDecimal[] { positiveProbability, negativeProbability };
	}

	/**
	 * Metoda pomocnicza do klasyfikacji w reprezentacji binarnej
	 * @param	site strona do klasyfikacji
	 * @return	wynik klasyfikacji
	 */
	private ClassificationResult classifyBinary(SiteInterface site) {
		return makeResult(computeBinaryProbabilities(site));
	}

	private BigDecimal[] computeTfProbabilities(SiteInterface site){
		int termCount;
		Website website = new Website(site.getUrl(), true);
		website.makeTermsMap(websiteFilters);
		Set<String> words = website.getTerms().keySet();
		BigDecimal positiveProbability = BigDecimal.valueOf(inClassProbability);
		BigDecimal negativeProbability = BigDecimal.valueOf(1.0 - inClassProbability);
		log.debug("[classifyTf] liczenie prawdopodobienstw [BEGIN]");
		for(String word : words){
			termCount = website.getTermCount(word);
			positiveProbability = positiveProbability.multiply(
				BigDecimal.valueOf(
					Math.pow(
						(getPositive().countTermInDocuments(word)+1.0) / (getPositive().getSumOfTerms()+2.0),
						termCount
					)
				)
			);
			negativeProbability = negativeProbability.multiply(
				BigDecimal.valueOf(
					Math.pow(
						(getNegative().countTermInDocuments(word)+1.0) / (getNegative().getSumOfTerms()+2.0),
						termCount
					)
				)
			);
		}
		log.debug("[classifyTf] liczenie prawdopodobienstw [END]");
		return new BigDecimal[] { positiveProbability, negativeProbability };
	}

	/**
	 * Metoda pomocnicza do klasyfikacji w reprezentacji częstotliwościowej
	 * @param site strona do klasyfikacji
	 * @return wynik klasyfikacji
	 */
	private ClassificationResult classifyTf(SiteInterface site) {
		return makeResult(computeTfProbabilities(site));
	}

	private ClassificationResult classifyMixedBinaryTf(SiteInterface site){
		double percent = 0.0, trueProb = 0.0, falseProb = 0.0;
		Boolean result = null;
		BigDecimal[] binaryProbabilities = computeBinaryProbabilities(site);
		BigDecimal[] tfProbabilities = computeTfProbabilities(site);
		if(binaryProbabilities[0].compareTo(binaryProbabilities[1]) == 0 || tfProbabilities[0].compareTo(tfProbabilities[1]) == 0){
			trueProb = binaryProbabilities[0].doubleValue();
			falseProb = binaryProbabilities[1].doubleValue();
			result = null;
		}else if(binaryProbabilities[0].compareTo(binaryProbabilities[1]) == 1){
			result = true;
			trueProb = binaryProbabilities[0].doubleValue();
			falseProb = binaryProbabilities[1].doubleValue();
			percent = binaryProbabilities[0].divide(binaryProbabilities[0].add(binaryProbabilities[1]), 100, RoundingMode.HALF_UP).doubleValue();
		} else if(tfProbabilities[0].compareTo(tfProbabilities[1]) != 1){
			result = false;
			trueProb = tfProbabilities[0].doubleValue();
			falseProb = tfProbabilities[1].doubleValue();
			percent = binaryProbabilities[1].divide(binaryProbabilities[1].add(binaryProbabilities[0]), 100, RoundingMode.HALF_UP).doubleValue();
		} else if(binaryProbabilities[0].compareTo(BigDecimal.valueOf(0.0)) == 0 || tfProbabilities[1].compareTo(BigDecimal.valueOf(0.0)) == 0){
			result = null;
		} else{
			BigDecimal	binary = binaryProbabilities[1].divide(binaryProbabilities[0], 100, RoundingMode.HALF_UP),
						tf = tfProbabilities[0].divide(tfProbabilities[1], 100, RoundingMode.HALF_UP);
			if(binary.compareTo(tf) < 0){
				result = true;
				trueProb = tf.doubleValue();
				falseProb = binary.doubleValue();
				percent = tf.divide(tf.add(binary), 100, RoundingMode.HALF_UP).doubleValue();
			}else{
				result = false;
				trueProb = binary.doubleValue();
				falseProb = tf.doubleValue();
				percent = binary.divide(binary.add(tf), 100, RoundingMode.HALF_UP).doubleValue();
			}
		}
		return new ClassificationResult(
				"true=" + trueProb
			+	";false=" + falseProb
			, percent, result);
	}

	/**
	 * @return	pozytywny zbiór uczący
	 */
	public WebsitesCollection getPositive() {
		return positive;
	}

	/**
	 * @param	positive ustawia pozytywny zbiór uczący
	 */
	public void setPositive(WebsitesCollection positive) {
		this.positive = positive;
	}

	/**
	 * @return	negatywny zbiór uczący
	 */
	public WebsitesCollection getNegative() {
		return negative;
	}

	/**
	 * @param negative ustawia negatywny zbiór uczący
	 */
	public void setNegative(WebsitesCollection negative) {
		this.negative = negative;
	}

	@Override
	public void storeToFile(String path) throws ClassificationSystemException{
		try{
			FileOutputStream fos = new FileOutputStream(path);
			GZIPOutputStream gz = new GZIPOutputStream(fos);
			ObjectOutputStream oos = new ObjectOutputStream(gz);
			oos.writeObject(this);
			oos.close();
		}catch(Exception e){
			log.error("[zapisywanie] Nie udalo sie zapisac klasyfikatora do pliku: " + path + " (" + e.getMessage() + ")");
		}
	}

	private ClassificationResult makeResult(BigDecimal[] probabilities){
		double percent = 0.0;
		Boolean result;
		if(probabilities[0].compareTo(probabilities[1]) == 0)
			result = null;
		else if(probabilities[0].compareTo(probabilities[1]) == 1){
			result = true;
			percent = probabilities[0].divide(probabilities[0].add(probabilities[1]), 100, RoundingMode.HALF_UP).doubleValue();
		} else{
			result = false;
			percent = probabilities[1].divide(probabilities[1].add(probabilities[0]), 100, RoundingMode.HALF_UP).doubleValue();
		}
		return new ClassificationResult(
				"true=" + probabilities[0].doubleValue()
			+	";false="+probabilities[1].doubleValue()
			, percent, result);
	}

}
