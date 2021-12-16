
package tiefighter;

import appboot.LARVABoot;


public class Practica3 {
    public static void main(String[] args) {
        LARVABoot connection = new LARVABoot();
        
        connection.Boot("isg2.ugr.es", 1099);
        connection.launchAgent("AMK-P3-1", Practica3Destroyer.class);
        connection.WaitToShutDown();
       
    }
    
}
