package pt.tecnico.hdlt.T25.client.Domain;

import java.util.List;
import java.util.Optional;

public class SystemInfo {
    private List<Location> grid;
    private int numberOfUsers;
    private int step;
    private int maxEp;
    private int durationEp = Integer.MAX_VALUE;
    private int currentEp;

    public SystemInfo() {
    }

    public SystemInfo(List<Location> grid, int numberOfUsers, int step, int maxEp) {
        this.grid = grid;
        this.numberOfUsers = numberOfUsers;
        this.step = step;
        this.maxEp = maxEp;
        this.currentEp = 0;
    }

    public List<Location> getGrid() {
        return grid;
    }

    public void setGrid(List<Location> grid) {
        this.grid = grid;
    }

    public int getNumberOfUsers() {
        return numberOfUsers;
    }

    public void setNumberOfUsers(int numberOfUsers) {
        this.numberOfUsers = numberOfUsers;
    }

    public int getStep() {
        return step;
    }

    public void setStep(int step) {
        this.step = step;
    }

    public int getMaxEp() {
        return maxEp;
    }

    public void setMaxEp(int maxEp) {
        this.maxEp = maxEp;
    }

    public int getDurationEp() {
        return durationEp;
    }

    public void setAutomaticTransitions(boolean automatic, Optional<Integer> durationEp) {
        if (!automatic) {
            this.durationEp = Integer.MAX_VALUE;
        } else {
            durationEp.ifPresent(integer -> this.durationEp = integer);
        }
    }

    public int getCurrentEp() {
        return currentEp;
    }

    public void updateCurrentEp() {
        currentEp++;
    }
}
