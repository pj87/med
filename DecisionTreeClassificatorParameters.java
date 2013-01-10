package implementation.classifiers;

import implementation.model.DocumentType;
import interfaces.ClassificatorParametersInterface;

public class DecisionTreeClassificatorParameters implements ClassificatorParametersInterface {

	private DocumentType format;

	public DecisionTreeClassificatorParameters(){
		format = DocumentType.BINARY;
	}

	/**
	 * @return the format
	 */
	public DocumentType getFormat() {
		return format;
	}

	/**
	 * @param format the format to set
	 */
	public void setFormat(DocumentType format) {
		this.format = format;
	}
	
}
