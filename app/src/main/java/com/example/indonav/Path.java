package com.example.indonav;

public class Path {

    private int dir, steps;
    public Path() {}
    public Path(int dir, int steps) {
        this.dir = dir;
        this.steps = steps;
    }

    public int getDir() {
        return dir;
    }

    public void setDir(int dir) {
        this.dir = dir;
    }

    public int getSteps() {
        return steps;
    }

    public void setSteps(int steps) {
        this.steps = steps;
    }

    public void setPath(int dir, int steps) {
        this.dir = dir;
        this.steps = steps;
    }
}
