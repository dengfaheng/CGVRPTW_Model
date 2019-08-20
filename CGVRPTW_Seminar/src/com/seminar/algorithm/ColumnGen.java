package com.seminar.algorithm;


import ilog.concert.*;
import ilog.cplex.*;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.*;

public class ColumnGen {
	public Depot depot_start;
	public Depot depot_end;
	public Map<Integer,Customer> all_customers = new HashMap<Integer,Customer>();
	public Map<Integer,Node> all_nodes = new HashMap<Integer,Node>();
	public List<Path> paths = new ArrayList<Path>();
	public MasterProblem masterproblem;
	public SubProblem subproblem;
	public Logger logger = new Logger();
	public Timer watch;
	private String instance;
	private class Node {
		public int id;
		public int id_external;
		private double xcoord;
		private double ycoord;
		private double t_at_node;
		public Node(int external_id, double x, double y, double t) {
			this.id = all_nodes.size();
			this.id_external = external_id;
			this.xcoord = x;
			this.ycoord = y;
			this.t_at_node = t;
			all_nodes.put(this.id, this);
		}
		public double time_to_node(Node node_to) {
			return Math.sqrt(Math.pow(this.xcoord-node_to.xcoord, 2)+Math.pow(this.ycoord-node_to.ycoord, 2));
		}
		public double time_at_node() {
			return t_at_node;
		}
	}
	private class Depot extends Node {
		public Depot(int external_id, double x, double y) {
			super(external_id,x,y,0);
		}
	}
	private class Customer extends Node{
		private double demand;
		private double ready_time;
		private double due_date;
		public Customer(int external_id, double x, double y, double demand, double ready_time, double due_date, double service_time) {
			super(external_id,x,y,service_time);
			this.demand = demand;
			this.ready_time = ready_time;
			this.due_date = due_date;
			all_customers.put(this.id, this);
		}
		public double a() {
			return ready_time;
		}
		public double b() {
			return due_date;
		}
		public double d() {
			return demand;
		}
	}
	private class Path {
		public int id;
		public List<Customer> customers;
		public IloNumVar y;
		public double cost;
		public Path(List<Integer> stops_new_path) {
			customers = new ArrayList<Customer>();
			for (int i=1; i<stops_new_path.size()-1; i++) {
				customers.add(all_customers.get(stops_new_path.get(i)));
			}
			calculateCost();
			id = paths.size();
			paths.add(this);
		}
		private void calculateCost() {
			if (customers.size()>0) {
				cost = depot_start.time_to_node(customers.get(0))+customers.get(0).time_at_node();
				for (int i=1; i<customers.size(); i++) {
					cost += customers.get(i-1).time_to_node(customers.get(i))+customers.get(i).time_at_node();
				}
				cost += customers.get(customers.size()-1).time_to_node(depot_end);
			}
			else {
				cost = 0;
			}
		}
		private int a(Customer customer) {
			return customers.contains(customer)? 1 : 0;
		}
		public double displayInfo() {
			double trvalCost = 0;
			System.out.println("Path id   : "+id);
			System.out.print(  "Stops     : depot->");
			trvalCost += depot_start.time_to_node(customers.get(0));
			for (int i=1; i<customers.size(); i++) {
				trvalCost += customers.get(i-1).time_to_node(customers.get(i));
			}
			trvalCost += customers.get(customers.size()-1).time_to_node(depot_end);
			for (Customer c : customers) {
				System.out.print(c.id_external+"->");
			}
			System.out.println("depot");
			System.out.println("trvalCost : "+trvalCost);
			return trvalCost;
		}
		
	}
	public class MasterProblem {
		public IloCplex cplex;
		private IloObjective total_cost;
		private Map<Customer,IloRange> row_customers = new HashMap<Customer,IloRange>();
		private Map<Customer,Double> pi = new HashMap<Customer,Double>();
		private List<IloConversion> mipConversion = new ArrayList<IloConversion>();
		public double lastObjValue;
		public MasterProblem() {
			createModel();
			createDefaultPaths();
			Parameters.configureCplex(this);
		}
		public void createDefaultPaths() {
			for (Customer c : all_customers.values()) {
				List<Integer> new_path = new ArrayList<Integer>();
				new_path.add(depot_start.id);
				new_path.add(c.id);
				new_path.add(depot_end.id);
				addNewColumn(new Path(new_path));
			}
		}
		public void createModel() {
			try{
				cplex = new IloCplex();
				total_cost = cplex.addMinimize();
				for (Customer customer : all_customers.values()) row_customers.put(customer, cplex.addRange(1, 1, "cust "+customer.id));
			}
			catch (IloException e) {
				System.err.println("Concert exception caught: " + e);
			}
		}
		public void addNewColumn(Path path) {
			try {
				IloColumn new_column = cplex.column(total_cost, path.cost);
				for (Customer c : all_customers.values()) new_column = new_column.and(cplex.column(row_customers.get(c),path.a(c)));
				path.y = cplex.numVar(new_column, 0, 1, "y."+path.id);
			}
			catch (IloException e) {
				System.err.println("Concert exception caught: " + e);
			}
		}
		public void solveRelaxation() {
			//System.getProperty("user.dir");
			try {
				//System.out.println(cplex.getModel());
				if (cplex.solve()) {
					saveDualValues();
					lastObjValue = cplex.getObjValue();
					//System.out.print("[ ");
					//for (Customer c : all_customers.values()) System.out.print(String.format("%3.2f", pi.get(c))+" ");
					//System.out.print(" ]\n");
				}
			}
			catch (IloException e) {
				System.err.println("Concert exception caught: " + e);
			}
		}
		public void saveDualValues() {
			try {
				for (Customer c : all_customers.values()) pi.put(c, cplex.getDual(row_customers.get(c)));
			}
			catch (IloException e) {
				System.err.println("Concert exception caught: " + e);
			}
		}
		public void solveMIP() {
			try {
				convertToMIP();
				if (cplex.solve()) {
					displaySolution();
					//logger.writeLog(instance, cplex.getObjValue(), cplex.getBestObjValue());
				}
				else {
					System.out.println("Integer solution not found");
				}
			}
			catch (IloException e) {
				System.err.println("Concert exception caught: " + e);
			}
		}
		public void convertToMIP() {
			try {
				for (Path path : paths) {
					mipConversion.add(cplex.conversion(path.y, IloNumVarType.Bool)) ;
					cplex.add(mipConversion.get(mipConversion.size()-1));
				}
			}
			catch (IloException e) {
				System.err.println("Concert exception caught: " + e);
			}
		}
		public void displaySolution() {
			try {
				double totalCost = 0;
				System.out.println("\n" + "--- Solution >>> ------------------------------");
				for (Path path : paths) {
					if (cplex.getValue(path.y)>0.99999) {
						totalCost += path.displayInfo();
					}
				}
				System.out.println("Total cost = "+totalCost);
				System.out.println("\n" + "--- Solution <<< ------------------------------");
			}
			catch (IloException e) {
				System.err.println("Concert exception caught: " + e);
			}
		}
	}
	public class SubProblem {
		public IloCplex cplex;
		private IloIntVar[][] x;
		private IloNumVar[] s;
		private IloObjective reduced_cost;
		private IloLinearNumExpr num_expr;
		private List<IloConstraint> constraints;
		public MyMipCallBack mip_call_back;
		public double lastObjValue;
		public double lastObjValueRelaxed;
		private class MyMipCallBack extends IloCplex.MIPInfoCallback {
			private boolean aborted;
			private double time_start;
			private double time_limit;
			public void main() {
				try {
					if (!aborted && hasIncumbent()) {
						double time_used = getCplexTime() - time_start;
						if ((getIncumbentObjValue() < Parameters.ColGen.subproblemObjVal) 
								|| (time_used > time_limit) 
								|| (getBestObjValue() > Parameters.ColGen.zero_reduced_cost) ) {
							System.out.println("aborted >>> ");
							aborted = true;
							abort();
						}
					}
				} catch (IloException e) {
					System.err.println("Concert exception caught: " + e);
				}
			}
			public void reset() {
				try {
					aborted = false;
					time_start = cplex.getCplexTime();
					time_limit = Parameters.ColGen.subproblemTiLim;
				} catch (IloException e) {
					System.err.println("Concert exception caught: " + e);
				}
			}
		}
		public SubProblem() {
			this.constraints = new ArrayList<IloConstraint>();
			createModel();
			setPriority();
			Parameters.configureCplex(this);
			this.mip_call_back = new MyMipCallBack();
			try {
				cplex.use(mip_call_back);
			} catch (IloException e) {
				System.err.println("Concert exception caught: " + e);
			}
		}
		public void setPriority() {
			try {
				for (int j : all_customers.keySet())
					cplex.setPriority(x[depot_start.id][j], 1);
			} catch (IloException e) {
				System.err.println("Concert exception caught: " + e);
			}
		}
		public void createModel() {
			try {
				// define model
				cplex = new IloCplex();
				// define variables
				x = new IloIntVar[all_nodes.size()][];
				for (int i : all_nodes.keySet()) {
					x[i] = cplex.boolVarArray(all_nodes.size());
					for (int j : all_nodes.keySet()) {
						x[i][j].setName("x."+i+"."+j);
					}
				}
				s = cplex.numVarArray(all_nodes.size(), 0, Double.MAX_VALUE);
				// define parameters
				double M = 0;
				for (int i=0; i<all_customers.size(); i++) 
					for (int j=0; j<all_customers.size(); j++) {
						double val = all_customers.get(i).b()
								+ all_customers.get(i).time_to_node(all_customers.get(j))
								+ all_customers.get(i).time_at_node()
								- all_customers.get(j).a();
						if (M<val) M=val;
					}
				double q = Parameters.capacity;
				// set objective
				reduced_cost = cplex.addMinimize();
				// set constraint : capacity
				num_expr = cplex.linearNumExpr();
				for (int i : all_customers.keySet())
					for (int j : all_nodes.keySet())
						num_expr.addTerm( all_customers.get(i).d(), x[i][j]);
				constraints.add(cplex.addLe(num_expr, q, "c1"));
				// set constraint : start from depot
				num_expr = cplex.linearNumExpr();
				for (int j : all_nodes.keySet())
					num_expr.addTerm(1.0, x[depot_start.id][j]);
				constraints.add(cplex.addEq(num_expr, 1, "c2"));
				// set constraint : flow conservation
				for (int h : all_customers.keySet()) {
					num_expr = cplex.linearNumExpr();
					for (int i : all_nodes.keySet())
						num_expr.addTerm(1.0, x[i][h]);
					for (int j : all_nodes.keySet())
						num_expr.addTerm(-1.0, x[h][j]);
					constraints.add(cplex.addEq(num_expr, 0, "c3"));
				}
				// set constraint : end at depot
				num_expr = cplex.linearNumExpr();
				for (int i : all_nodes.keySet())
					num_expr.addTerm(1.0, x[i][depot_end.id]);
				constraints.add(cplex.addEq(num_expr, 1, "c4"));
				// set constraint : sub tour elimination
				for (int i : all_nodes.keySet())
					for (int j : all_nodes.keySet()) {
						double t_ij = all_nodes.get(i).time_at_node()+all_nodes.get(i).time_to_node(all_nodes.get(j));
						num_expr = cplex.linearNumExpr();
						num_expr.addTerm( 1.0, s[i]);
						num_expr.addTerm(-1.0, s[j]);
						num_expr.addTerm( M  , x[i][j]);
						constraints.add(cplex.addLe(num_expr, M-t_ij, "c5"));
					}
				// set constraint : time windows
				for (int i : all_customers.keySet()) {
					constraints.add(cplex.addGe(s[i], all_customers.get(i).a(), "c6"));
					constraints.add(cplex.addLe(s[i], all_customers.get(i).b(), "c6"));
				}
				//prohibited moves
				num_expr = cplex.linearNumExpr();
				for (int i : all_nodes.keySet()) {
					num_expr.addTerm(1.0, x[depot_end.id][i]);
					num_expr.addTerm(1.0, x[i][depot_start.id]);
					num_expr.addTerm(1.0, x[i][i]);
				}
				constraints.add(cplex.addEq(num_expr, 0, "c7"));
			} catch (IloException e) {
				System.err.println("Concert exception caught: " + e);
			}
		}
		public void updateReducedCost() {
			try {
				IloLinearNumExpr num_expr = cplex.linearNumExpr();
				for (int i : all_nodes.keySet()) {
					for (int j : all_nodes.keySet()) {
						double t_ij = all_nodes.get(i).time_at_node()+all_nodes.get(i).time_to_node(all_nodes.get(j));
						if (all_customers.keySet().contains(i))
							num_expr.addTerm(t_ij - ColumnGen.this.masterproblem.pi.get(all_customers.get(i)), x[i][j]);
						else
							num_expr.addTerm(t_ij , x[i][j]);
					}
				}
				reduced_cost.clearExpr();
				reduced_cost.setExpr(num_expr);
			}
			catch (IloException e) {
				System.err.println("Concert exception caught: " + e);
			}
		}
		public void solve() {
			try {
				mip_call_back.reset();
				if (cplex.solve()) {
					//System.out.println(cplex.getModel());
					this.lastObjValue = cplex.getObjValue();
					this.lastObjValueRelaxed = cplex.getBestObjValue();
					int nPool = cplex.getSolnPoolNsolns();
					for (int i=0; i<nPool; i++) {
						if (cplex.getObjValue(i) < Parameters.ColGen.zero_reduced_cost) {
							savePath(i);
						}
					}
				}
			}
			catch (IloException e) {
				System.err.println("Concert exception caught: " + e);
			}
		}
		public void savePath(int nSol) {
			try {
				//print
				/*for (int i : all_nodes.keySet())
					for (int j : all_nodes.keySet())
						if (cplex.getValue(x[i][j],nSol) > 0.99999)
							System.out.println(i+","+j);*/
				//save
				List<Integer> stops_new_path = new ArrayList<Integer>();
				for (int i : all_customers.keySet()) {
					if (cplex.getValue(x[depot_start.id][i],nSol) > 0.99999) {
						stops_new_path.add(depot_start.id);
						stops_new_path.add(i);
						while (i!=depot_end.id) {
							for (int j : all_nodes.keySet()) {
								if (cplex.getValue(x[i][j],nSol) > 0.99999) {
									stops_new_path.add(j);
									i = j;
									break;
								}
							}
						}
						break;
					}
				}
				ColumnGen.this.masterproblem.addNewColumn(new Path(stops_new_path));
			}
			catch (IloException e) {
				System.err.println("Concert exception caught: " + e);
			}
		}
	}
	public ColumnGen(String instance) {
		this.instance = instance;
		ReadDataFromFile();
		masterproblem = new MasterProblem();
		subproblem = new SubProblem();
		watch = new Timer();
	}

	private void ReadDataFromFile() {
		try {
			System.out.println(this.instance);
			Scanner in = new Scanner(new FileReader(this.instance));
			
			// skip unusefull lines
			in.nextLine(); // skip filename
			in.nextLine(); // skip empty line
			in.nextLine(); // skip vehicle line
			in.nextLine();
			int vehiclesNr	= in.nextInt();
			
			// read Q
			double vehiclesCapacity = in.nextInt();
			
			// skip unusefull lines
			in.nextLine();
			in.nextLine();
			in.nextLine();
			in.nextLine();
			in.nextLine();
			
			// read depots data
			int id_external_depot = in.nextInt();
			double xcoord_depot   = in.nextDouble();
			double ycoord_depot   = in.nextDouble();
			
			
			
			in.nextDouble();
			in.nextInt();
			in.nextInt();
			in.nextDouble();
			
			// read customers data
			while(in.hasNextInt())
			{	
				int id_external      = in.nextInt();
				double xcoord        = in.nextDouble();
				double ycoord        = in.nextDouble();
				double demand        = in.nextDouble();
				double readyt        = in.nextInt();
				double duedate       = in.nextInt();
				double servicet      = in.nextDouble();	
				// add customer to customers list
				new Customer(id_external, xcoord, ycoord, demand, readyt, duedate, servicet);
			}// end for customers
			
			depot_start = new Depot(id_external_depot,xcoord_depot,ycoord_depot);
			depot_end = new Depot(id_external_depot,xcoord_depot,ycoord_depot);
			
			in.close();
			
		} catch (FileNotFoundException e) {
			// File not found
			System.out.println("File not found!");
			System.exit(-1);
		}
	}
	
	public void runColumnGeneration() {
		int iteration_counter = 0;
		watch.start();
		do {
			
			iteration_counter++;
			masterproblem.solveRelaxation();
			subproblem.updateReducedCost();
			watch.start();
			subproblem.solve();
			watch.stop();
			displayIteration(iteration_counter);
		} while (subproblem.lastObjValue<Parameters.ColGen.zero_reduced_cost_AbortColGen);
		masterproblem.solveMIP();
	}
	private void displayIteration(int iter) {
		if ((iter)%20==0 || iter==1) {
			System.out.println();
			System.out.print("Iteration");
			System.out.print("     SbTime");
			System.out.print("   nPaths");
			System.out.print("       MP lb");
			System.out.print("       SB lb");
			System.out.print("      SB int");
			System.out.println();
		}
		System.out.format("%9.0f", (double)iter);
		System.out.format("%9.1f", watch.getSecond());
		System.out.format("%9.0f", (double)paths.size());
		System.out.format("%12.4f", masterproblem.lastObjValue);//master lower bound
		System.out.format("%12.4f", subproblem.lastObjValueRelaxed);//sb lower bound
		System.out.format("%12.4f", subproblem.lastObjValue);//sb lower bound
		System.out.println();
	}
}
