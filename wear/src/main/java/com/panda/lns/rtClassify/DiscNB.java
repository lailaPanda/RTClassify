package com.panda.lns.rtClassify;

public class DiscNB{
    DiscNBClass[] classes=null;
    DiscNB(DiscNBClass[] classes){
        this.classes=classes;
    }
    int getPrediction(double[] values){
        double[] probabilities = new double[classes.length];
        double tmp=1;
        for(int i=0;i<classes.length;i++){
            for(int k=0;k<classes[i].getPredictors().length;k++){
                tmp*=classes[i].getPredictors()[k].getProbability(values[k]);
            }
            tmp*=classes[i].getP();
            probabilities[i] = tmp;
            tmp=1;
        }
        double max=0;
        int maxIndex=-1;
        for(int i=0;i<probabilities.length;i++){
            if(probabilities[i] > max){
                max=probabilities[i];
                maxIndex=i;
            }
        }
        return maxIndex;
    }
}

class DiscNBClass{
    private double p=0;
    private String name="";
    private DiscNBPredictor[] predictors=null;
    DiscNBClass(String name, double p, DiscNBPredictor[] predictors){
        this.name=name;
        this.p=p;
        this.predictors=predictors;
    }
    double getP(){
        return p;
    }
    String getName(){
        return name;
    }
    DiscNBPredictor[] getPredictors(){
        return predictors;
    }
}

class DiscNBPredictor{
    private int regions=0;
    private double[] ranges=null;
    private double[] p=null;

    DiscNBPredictor(int regions, double[] ranges, double[] p){
        this.regions=regions;
        this.ranges = ranges;
        this.p=p;
    }
    double getProbability(double value){
        int n=-1;
        for(int i=0;i<ranges.length;i++){
            if(value < ranges[i]){
                n=i;
                break;
            }
            n=i;
        }
        return p[n];
    }
}
