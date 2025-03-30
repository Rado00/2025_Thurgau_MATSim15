package abmt2025.project.utils.classSamples;

import com.google.inject.Inject;
import com.google.inject.name.Named;

public class Link {

	private final String name;

	
	@Inject
	public Link(String name) {

		this.name = name;
	}
	
//  we can also provide named objects to Inject constructors	
//	@Inject
//	public Link(@Named("StreetName") String name) {
//
//		this.name = name;
//	}

	public String getName() {
		return name;
	}
}
