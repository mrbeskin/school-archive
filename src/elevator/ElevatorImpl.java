package elevator;

import Request.Request;
import building.Building;
import core.Direction;
import core.Main;
import exception.FloorOutOfBoundsException;
import person.Person;

import java.util.ArrayList;
import java.util.Collections;

/**
 * The implementation class of the basic elevator.
 *
 * Created by michael on 2/8/15.
 */
public class ElevatorImpl implements Runnable, Elevator {
    private final int homeFloor = 1;
    private final int occupancy = 5;
    private final long travelTime = 500;
    private final long doorTime = 250;

    private Direction direction;

    private boolean active;

    private int elevatorID;
    private int currentFloor;
    private boolean returning = false;

    private boolean consuming;

    private ArrayList<Request> riderRequests = new ArrayList<Request>();
    private ArrayList<Request> floorRequests = new ArrayList<Request>();
    private ArrayList<Person> occupants = new ArrayList<Person>();

    public ElevatorImpl(int idNum, int startingFloor) {
        elevatorID = idNum;
        currentFloor = startingFloor;
    }


    /*
     * The following methods are core methods
     * to run the the thread
     */

    // The main thread run
    public void run() {
        active = true;
        while (active) {
            // are there any rider or floor requests
            synchronized (floorRequests) {
                if (!riderRequests.isEmpty() || !floorRequests.isEmpty()) {
                    consuming = true;
                } else {
                    consuming = false;
                }
            }
            // if there are, process requests
            if (consuming == true) {
                System.out.println("Processing requests"); //TODO: remove
                processRequests();
                // else check if there are more requests
            } else {
                //TODO: check controller pending requests
            }
            // if there are no more requests set returning to true
            synchronized (floorRequests) {
                if(floorRequests.isEmpty() && riderRequests.isEmpty()) {
                    returning = true;
                }
            }
            // if returning is true wait for timeout or more requests
            if (returning == true) {
                if (currentFloor != homeFloor) {
                    try {
                        System.out.println("I'm sleeping"); //TODO: remove
                        Thread.sleep(1500);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                else {
                    try {
                    synchronized (floorRequests) {
                        if (floorRequests.isEmpty() && riderRequests.isEmpty()) {
                            System.out.printf("%s Elevator %d has no requests and is waiting for input.\n",
                                    Main.currentTime(), elevatorID);
                            direction = Direction.IDLE;
                            floorRequests.wait();
                        }
                    }
                } catch (InterruptedException ex) {
                    System.out.println(ex);
                }
                }
            }
            // if still no more requests
            synchronized (floorRequests) {
                if (riderRequests.isEmpty() && floorRequests.isEmpty()) {
                    returning = true;
                    System.out.println("RETURNING CONDITION REACHED"); //TODO: remove
                }
            }

            if (returning == true){
                if (currentFloor != homeFloor) {
                    addCallRequest(new Request(currentFloor, 1));
                    System.out.println("going home"); //TODO: remove
                }
            }
        }
    }

    private void processRequests() {
        while (consuming) {
            if (direction == Direction.IDLE) {
                synchronized (floorRequests){
                    setDirection();
                    System.out.println(direction);
                }
            } else if (onRequestedFloor()) {
                arrive();
            } else {
                try {
                    move();
                } catch (FloorOutOfBoundsException flex) {
                    System.out.print(flex);
                }
            }
            if (floorRequests.isEmpty() && riderRequests.isEmpty()){
                consuming = false;
            }
        }
    }

    /*
     * The following methods define movement
     * and arrival.
     */

    // Move to next floor according to direction
    public void move() throws FloorOutOfBoundsException{
        int departed = currentFloor;
        try {
            Thread.sleep(travelTime); // move
        } catch (InterruptedException e) {
            System.out.printf("Elevator %d move interrupted.\n", elevatorID);
        }
        if (direction == Direction.UP) {
            currentFloor++;
            if (currentFloor > Building.getInstance().getNumFloors()){
                throw new FloorOutOfBoundsException("Elevator went above the building.\n");
            }
        } else if (direction == Direction.DOWN) {
            currentFloor--;
            if (currentFloor <= 0){
                throw new FloorOutOfBoundsException("Elevator went below the building.\n");
            }
        }
        System.out.printf("%s Elevator %d moved from floor %d to floor %d %s\n",
                Main.currentTime(), elevatorID, departed, currentFloor, requestString());
    }

    // arrive at a floor. The only method that should allow removal of a request.
    public void arrive(){
        openDoor();
        getOff();
        removeRequests(); // remove the requests for this floor
        getRiders();
        closeDoor();


        synchronized (floorRequests) {
            if (floorRequests.isEmpty() && riderRequests.isEmpty()){
                direction = Direction.IDLE;
            }
        }
    }
    /*
     * The following methods pertain to the only two ways an elevator receives a request:
     * a rider pushing a button or the controller calling it to a floor.
     */

    public void pushButton(Request request) {
        if (request.getDirection() == direction){
            addButtonRequest(request);
            System.out.printf("%s Elevator %d accepting a(n) %s request to floor %d %s\n",
                    Main.currentTime(), elevatorID, request.getDirection(), request.getTargetFloor(), requestString());
        } else {
            System.out.printf("%s Elevator %d ignoring a(n) %s request on floor %d %s\n",
                    Main.currentTime(), elevatorID, request.getDirection(), currentFloor, requestString());
        }
    }

    public void call(Request request) {
        System.out.printf("%s Elevator %d responding to a call to floor %d %s\n",
                Main.currentTime(), elevatorID, request.getTargetFloor(), requestString());
        addCallRequest(request);
        direction = request.getDirection();
    }

    public void addCallRequest(Request r) {
        synchronized (floorRequests) {
            boolean alreadyThere = false;
            for (int i = 0; i < floorRequests.size(); i ++){
                if(floorRequests.get(i).getTargetFloor() == r.getTargetFloor()){
                    alreadyThere = true;
                }
            }
            if (!alreadyThere) {
                floorRequests.add(r);
            }
            requestSort();
            floorRequests.notifyAll();
        }
    }

    public void addButtonRequest(Request r){
        synchronized (floorRequests) {
            boolean alreadyThere = false;
            for (int i = 0; i < riderRequests.size(); i ++){
                if(riderRequests.get(i).getTargetFloor() == r.getTargetFloor()){
                    alreadyThere = true;
                }
            }
            if(!alreadyThere) {
                riderRequests.add(r);
            }
            requestSort();
            floorRequests.notifyAll();
        }
    }

    // add request methods called when request is decided to be sanitary

    /*
     * The following methods are for setting the direction of travel
     * and abstracting binary travel questions
     */
    private void setDirection(){
        synchronized (floorRequests) {
            if (!floorRequests.isEmpty() && !riderRequests.isEmpty()) {
                int floor = Math.abs(currentFloor - floorRequests.get(0).getTargetFloor());
                int rider = Math.abs(currentFloor - riderRequests.get(0).getTargetFloor());
                if (floor > rider){
                    direction = riderRequests.get(0).getDirection();
                } else {
                    goToFloorRequest(floorRequests.get(0));
                }
            }

            if (floorRequests.isEmpty() && !riderRequests.isEmpty()){
                    direction = riderRequests.get(0).getDirection();
                }
            }

            if (riderRequests.isEmpty() && !floorRequests.isEmpty()){
                if (floorRequests.get(0).getTargetFloor() < currentFloor) {
                    direction = Direction.DOWN;
                } else {
                    direction = Direction.UP;
                }
            }
        }

    // set direction to direction of floor request
    private void goToFloorRequest(Request request) {
        if (floorRequests.get(0).getTargetFloor() > currentFloor) {
            direction = Direction.UP;
        } else {
            direction = Direction.DOWN;
        }
    }

    // to check if there needs to be an arrival
    private boolean onRequestedFloor(){
        if (!riderRequests.isEmpty()) {
            if (riderRequests.get(0).getTargetFloor() == currentFloor) {
                return true;
            }
        }
            if(!floorRequests.isEmpty()){
                if (floorRequests.get(0).getTargetFloor() == currentFloor){
                    return true;
            }
        }
        return false;
    }

    /*
     * Methods for Opening door, closing door and shutting down
     * Very simple and should not need much work.
     */

    private void openDoor() {
        try {
            Thread.sleep(doorTime);
        } catch (InterruptedException e) {
            System.out.printf("Elevator %d door open interrupted.\n", elevatorID);
        }
        System.out.printf("%s Elevator %d doors open on floor %d\n",
                Main.currentTime(), elevatorID, currentFloor);
    }

    private void closeDoor() {
        try {
            Thread.sleep(doorTime);
        } catch (InterruptedException e) {
            System.out.println("Elevator %d door close interrupted.\n");
        }
        System.out.printf("%s Elevator %d doors closed on floor %d\n",
                Main.currentTime(), elevatorID, currentFloor);
    }

    public void shutDown() {
        synchronized (floorRequests) {
            active = false;
            System.out.printf ("%s " +
                            "Elevator %d shutting down.\n",
                    Main.currentTime(), elevatorID);
            floorRequests.notifyAll();
        }
    }

    /*
     * methods for interacting with the floor
     */

    // get off, get on, request floors, depart
    public void getOff() {

        // remove any people who have arrived
        for (int i = 0; i < occupants.size(); i++) {
            if (occupants.get(i).getTargetFloor() == currentFloor) {
                occupants.remove(i);
                i--;
            }
        }

    }

    /*
     * Methods for removing and adding from the request queues
     */

    private void removeRequests(){
        synchronized (floorRequests) {
            if (!floorRequests.isEmpty()) {
                if (currentFloor == floorRequests.get(0).getTargetFloor()) {
                    floorRequests.remove(0);
                }
            }
        }
        synchronized (riderRequests) {
            if(!riderRequests.isEmpty()) {
                if (currentFloor == riderRequests.get(0).getTargetFloor()) {
                    riderRequests.remove(0);
                }
            }
        }
    }

    private void getRiders() {
        // array list of people waiting on floor
        ArrayList<Person> potentialRiders = Building.getInstance().getFloor(currentFloor).elevatorArrival();
        // if no requests left, let a person on
        if(riderRequests.isEmpty() && floorRequests.isEmpty() && !potentialRiders.isEmpty()){
            Person first = potentialRiders.remove(0);
            direction = first.getDesiredDirection();
            pushButton(new Request(first.getStartFloor(), first.getTargetFloor()));
        }

        for (int i = 0; i < potentialRiders.size(); i++){
            if (occupants.size() < occupancy){
                if(potentialRiders.get(i).getDesiredDirection() == direction){
                    Person newOccupant = potentialRiders.remove(i);
                    occupants.add(newOccupant);
                    pushButton(new Request(newOccupant.getStartFloor(), newOccupant.getTargetFloor()));
                    i--;
                }
            }
        }
    }

    private void requestSort() {
        synchronized (floorRequests) {
            if (direction == Direction.UP) {
                Collections.sort(floorRequests);
            }
            if (direction == Direction.DOWN) {
                Collections.sort(floorRequests);
                Collections.reverse(floorRequests);
            }
        }
        synchronized (riderRequests){
            if (direction == Direction.UP) {
                Collections.sort(riderRequests);
            }
            if (direction == Direction.DOWN) {
                Collections.sort(riderRequests);
                Collections.reverse(riderRequests);
            }
        }
    }

    /*
     * string building method for real time reports
     */
    private String requestString(){
        String floorString = "[Floor Requests: ";
        String riderString = "[Rider Requests: ";
        String returnString;

        if(!floorRequests.isEmpty()) {
            for (int i = 0; i < floorRequests.size(); i++){
                floorString = floorString + floorRequests.get(i).getTargetFloor() + " ";
            }
        }
        if (!riderRequests.isEmpty()){
            for(int i = 0; i < riderRequests.size(); i ++){
                riderString = riderString + riderRequests.get(i).getTargetFloor() + " ";
            }
        }
        returnString = floorString + "]" + riderString + "]";
        return returnString;
    }

    /*
     * Methods to help get information for call algorithm
     */

    // to check if responding to a floor call
    public boolean isFloorCall(){
        if (direction == Direction.UP){
            if(riderRequests.get(0).getTargetFloor() > floorRequests.get(0).getTargetFloor()){
                return true;
            }
        }
        if (direction == Direction.DOWN){
            if(riderRequests.get(0).getTargetFloor() < floorRequests.get(0).getTargetFloor()){
                return true;
            }
        }
        return false;
    }

    public int getCurrentDestination(){
        if (floorRequests.isEmpty() && riderRequests.isEmpty()) {
            if(direction == Direction.UP) {
                if (floorRequests.get(0).getTargetFloor() < riderRequests.get(0).getTargetFloor()) {
                    return floorRequests.get(0).getTargetFloor();
                } else {
                    return riderRequests.get(0).getTargetFloor();
                }
            }

            if (direction == Direction.DOWN){
                if (floorRequests.get(0).getTargetFloor() > riderRequests.get(0).getTargetFloor()) {
                    return floorRequests.get(0).getTargetFloor();
                } else {
                    return riderRequests.get(0).getTargetFloor();
                }
            }
        }


        if (floorRequests.isEmpty()) {
            return riderRequests.get(0).getTargetFloor();
        }
        if(riderRequests.isEmpty()){
            return floorRequests.get(0).getTargetFloor();
        }
        return(-1);
    }


    /*
     * gets
     */

    public int getElevatorID() {
        return elevatorID;
    }

    public Direction getDirection() {
        return direction;
    }

    public int getCurrentFloor() {
        return currentFloor;
    }

    public boolean isConsuming() {
        return consuming;
    }

    public boolean isGoingTo(int floor){
        synchronized (floorRequests) {
            for (int i = 0; i < floorRequests.size(); i++) {
                if (floorRequests.get(i).getTargetFloor() == floor) {
                    return true;
                }
            }
        }
        synchronized (riderRequests) {
            for (int i = 0; i < riderRequests.size(); i++) {
                if (riderRequests.get(i).getTargetFloor() == floor) {
                    return true;
                }
            }
        }
        return false;
    }
}
