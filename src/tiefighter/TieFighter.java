
package tiefighter;

import appboot.LARVABoot;


public class TieFighter {
    public static void main(String[] args) {
        LARVABoot connection = new LARVABoot();
        
        connection.Boot("isg2.ugr.es", 1099);
//        connection.launchAgent("AMK-7", Practica1.class);
        connection.launchAgent("AMK-9", Practica2.class);
        connection.WaitToShutDown();
       
    }
    
}
