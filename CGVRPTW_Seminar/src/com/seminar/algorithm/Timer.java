package com.seminar.algorithm;

public class Timer{
	
	private long startTime;
	private long stopTime;
	private boolean running;
	private double elapsed;
  
  
	public Timer(){
		super();
	}

	public void reset(){
		this.startTime = 0;
		this.running = false;
	}

	public void start(){
		this.startTime = System.nanoTime();
		this.running = true;
	}
  
	public void stop(){
	  
		if (running){ 
			this.stopTime = System.nanoTime();
			this.running = false;
		}
		updateTime();
	}
  
	public void updateTime(){
		
		if (running){
			elapsed = ((System.nanoTime() - startTime));
		}
		else{
			elapsed = ((stopTime - startTime));
		}
	}
	
	public double getMillisSecond() {
		updateTime();
		return elapsed / 10e6;
	}
	
	public double getSecond() {
		updateTime();
		return elapsed / 10e9;
	}
	
	public double getMinute() {
		updateTime();
		return elapsed / 10e9 / 60;
	}
	
	public String getMillisSecondString() {
		return String.format("%8.4f", getMillisSecond())+" ms\n";
	}
	
	public String getSecondString() {
		return String.format("%8.4f", getSecond())+" s\n";
	}
	
	public String getMinuteString() {
		return String.format("%8.4f", getMinute())+" min\n";
	}
	
	public String toString() {
		StringBuffer print = new StringBuffer();
		print.append("\n" + "--- Total Time elapsed: ------------------------------");
		print.append("\n" + "| "+String.format("%4.0f", Math.floor(getMinute()))+" min " + 
								 String.format("%4.0f", Math.floor(getSecond())%60)+" s "+
								 String.format("%4.0f", Math.floor(getMillisSecond())%1000)+" ms");
		print.append("\n" + "------------------------------------------------------");
		
		return print.toString();
	}
  
  
}
