package abmt2023.project.mode_choice.variables;

import java.io.BufferedWriter;
import java.io.IOException;

import org.matsim.api.core.v01.Scenario;
import org.matsim.core.controler.events.IterationEndsEvent;
import org.matsim.core.controler.events.StartupEvent;
import org.matsim.core.controler.listener.IterationEndsListener;
import org.matsim.core.controler.listener.StartupListener;
import org.matsim.core.utils.io.IOUtils;

import com.google.inject.Inject;

import abmt2023.week4.exercises.counter.MyEventHandler;

public class ControlerListener implements StartupListener, IterationEndsListener{
	
	private final MyEventHandler eventHandler = new MyEventHandler();
	private final int[] enterEvents;
	private final int[] leaveEvents;
	private final Scenario scenario;
	
	public ControlerListener(Scenario scenario) {
		enterEvents = new int[scenario.getConfig().controler().getLastIteration() + 1];
		leaveEvents = new int[scenario.getConfig().controler().getLastIteration() + 1];
		this.scenario = scenario;

	}
	
	public void notifyStartup(StartupEvent event) {
		event.getServices().getEvents().addHandler(this.eventHandler);
	}
	
	public void notifyIterationEnds(IterationEndsEvent event) {
		
		//store event data per iteration
		this.enterEvents[event.getIteration()]= this.eventHandler.getCounterEnter();
		this.leaveEvents[event.getIteration()]= this.eventHandler.getCounterLeave();
		
		if (event.getIteration() == scenario.getConfig().controler().getLastIteration()) {
			
			// write all data gathered in csv files so specify output path
			String path = event.getServices().getControlerIO().getOutputPath() + "\\events.csv";
			try {
				//Because we want to write to CSV we would use the generic bufferred writer and specify the csv file name
		        BufferedWriter writer = IOUtils.getBufferedWriter(path);
		        writer.write("iteration,numlinkenter,numlinkleave\n");
				
				//write out the events using the bufferedWriter object
		        for (int i = 0;i <= event.getIteration();i++) {
		        	writer.write(i + "," + this.enterEvents[i] + "," + this.leaveEvents[i] + "\n");
		        }
				// flush() tells the Writer to output the stream
				writer.flush();
				
				// it is good practice to close the stream when no more output is expected
				writer.close();
				
			} catch (IOException e) {
				
				e.printStackTrace();
			}			
		}
	}

	public int[] getEnterEvents() {
		return enterEvents;
	}

	public int[] getLeaveEvents() {
		return leaveEvents;
	}

}
