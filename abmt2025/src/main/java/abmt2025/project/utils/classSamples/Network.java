package abmt2023.project.utils.classSamples;

import com.google.inject.Inject;

public class Network {
	
	private Link link;
	
	@Inject
	public Network(Link link) {
		this.link = link;
	}
	
	public Link getLink() {
		return this.link;
	}
}