package implementation.classifiers;

import exceptions.ClassificationSystemException;
import implementation.model.DocumentType;
import interfaces.ClassificationInfo;
import interfaces.ClassificatorFactoryInterface;
import interfaces.ClassificatorInterface;
import interfaces.ClassificatorParametersInterface;
import java.io.FileInputStream;
import java.io.ObjectInputStream;
import java.util.zip.GZIPInputStream;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.log4j.Logger;

public class DecisionTreeClassificatorFactory implements ClassificatorFactoryInterface {

    private static Logger log = Logger.getLogger(DecisionTreeClassificatorFactory.class);

    @Override
    public ClassificatorInterface getInstance(ClassificatorParametersInterface parameters) throws ClassificationSystemException{
        DecisionTreeClassificator classificator = new DecisionTreeClassificator();
        if(!(parameters instanceof DecisionTreeClassificatorParameters))
        	throw new ClassificationSystemException("Unexpected parameters type " + parameters.getClass().toString() + " for factory " + this.getClass().getCanonicalName() + " ");
        classificator.configure((DecisionTreeClassificatorParameters)parameters);
        return classificator;
    }

    @Override
    public ClassificatorParametersInterface parseConfigurationString(String configurationString) {
    	String[] args = configurationString.split(" ");
		DecisionTreeClassificatorParameters params = new DecisionTreeClassificatorParameters();
		Options options = new Options().addOption("f", "format", true, "Format dokumentu (binary, tf, mixed)- domyslnie binary");
		CommandLineParser cmdLineGnuParser = new GnuParser();
		try {
			CommandLine cmd = cmdLineGnuParser.parse(options, args);
			
			if(cmd.hasOption("f")){
				if(cmd.getOptionValue("f").equals("tf"))
					params.setFormat(DocumentType.TF);
				else if(cmd.getOptionValue("f").equals("mixed"))
					params.setFormat(DocumentType.MIXED);
			}
		} catch (ParseException e) {
			log.error("[parsowanie parametrow] Blad parsowania: " + e.getMessage());
		}
		return params;
    }

    @Override
    public ClassificatorInterface getFromFile(String path) {
        DecisionTreeClassificator classificator = new DecisionTreeClassificator();
        try {
            FileInputStream fin = new FileInputStream(path);
            GZIPInputStream gz = new GZIPInputStream(fin);
            ObjectInputStream ois = new ObjectInputStream(gz);
            classificator = (DecisionTreeClassificator) ois.readObject();
            ois.close();
        } catch (Exception ex) {
            log.error("[tworzenie] Nie udalo sie wczytac klasyfikatora z pliku: " + path);
        }
        return classificator;
    }

    @Override
    public ClassificationInfo parseClassificatonInfo(String info) {
        //@TODO implement
        throw new UnsupportedOperationException("Not supported yet.");
    }
}

