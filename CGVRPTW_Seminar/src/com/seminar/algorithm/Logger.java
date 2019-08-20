package com.seminar.algorithm;


public class Logger {
	private StopWatch st;
	private class StopWatch {
		  private long startTime = 0;
		  private long stopTime = 0;
		  private boolean running = false;
		  public void start() {
		    this.startTime = System.currentTimeMillis();
		    this.running = true;
		  }
		  public void stop() {
		    this.stopTime = System.currentTimeMillis();
		    this.running = false;
		  }
		  //elapsed time in seconds
		  public long elapsedTimeSec() {
		    long elapsed;
		    if (running) {
		      elapsed = ((System.currentTimeMillis() - startTime) / 1000);
		    }
		    else {
		      elapsed = ((stopTime - startTime) / 1000);
		    }
		    return elapsed;
		  }
		} 
	public Logger() {
		st = new StopWatch();
		st.start();
	}
	
	public double timeStamp() {
		return st.elapsedTimeSec();
	}
    
}