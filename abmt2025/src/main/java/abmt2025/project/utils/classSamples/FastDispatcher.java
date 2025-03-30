package abmt2025.project.utils.classSamples;

import com.google.inject.Inject;

public class FastDispatcher implements Dispatcher {
	

	@Inject
	public FastDispatcher() {
		
	}
	
	

	@Override
	public void dispatch() {
		// do something that is really fast here
		System.out.println("we are doing fast dispatching!");
	}

}
