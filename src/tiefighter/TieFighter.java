
package tiefighter;

import appboot.LARVABoot;


public class TieFighter {
    public static void main(String[] args) {
        LARVABoot connection = new LARVABoot();
        
        connection.Boot("isg2.ugr.es", 1099);
        connection.launchAgent("Ray", MyFirstTieFighter.class);
        connection.WaitToShutDown();
       
    }
    
}
