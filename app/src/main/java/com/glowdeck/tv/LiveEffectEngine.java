package com.glowdeck.tv;

/** Deterministic, device-independent RGB animation renderer for WLED's DNRGB UDP protocol.
 * Pixel coordinate zero is the first LED of the selected physical segment. No Android API calls.
 */
final class LiveEffectEngine {
    enum Effect { CHASE, COLOR_WIPE, RAINBOW_FLOW, PULSE, SPARKLE }

    static final class Settings {
        final Effect effect;
        final int speed;       // 1..100
        final int brightness;  // 1..100, relative to the user's brightness
        final int primary;
        final int secondary;
        Settings(Effect effect, int speed, int brightness, int primary, int secondary) {
            if (effect == null) throw new IllegalArgumentException("Effect required");
            this.effect=effect;
            this.speed=limit(speed,1,100);
            this.brightness=limit(brightness,1,100);
            this.primary=primary;
            this.secondary=secondary;
        }
    }
    private LiveEffectEngine() {}
    static int[] render(int ledCount, long elapsedMs, Settings config) {
        if (config == null || ledCount < 1 || ledCount > 65536)
            throw new IllegalArgumentException("Invalid live effect configuration or LED count");
        long now=Math.max(0,elapsedMs);
        int[] out=new int[ledCount];
        // Velocity: at speed 50, a head moves ~20 pixels / second.
        double ledPerMs=(3.0 + config.speed*0.34)/1000.0;
        int head=(int)(((long)(now*ledPerMs))%ledCount);
        long tick=now/Math.max(60,330-config.speed*2);
        double phase=(now/1000.0)*(0.25+config.speed*0.025);
        for (int i=0;i<ledCount;i++) {
            int rgb;
            switch(config.effect) {
                case CHASE: {
                    int distance=(head-i+ledCount)%ledCount;
                    double weight=distance==0?1:distance<9?Math.pow(0.62,distance):0;
                    rgb=blend(config.secondary,config.primary,weight);
                    break;
                }
                case COLOR_WIPE: {
                    // Fill from left to right, then reset to secondary. No global effects are modified.
                    int position=(int)((now*ledPerMs)%((long)ledCount+1));
                    rgb=i<position?config.primary:config.secondary;
                    break;
                }
                case RAINBOW_FLOW: {
                    double hue=(360.0*i/Math.max(ledCount,1)+phase*45)%360.0;
                    rgb=hsv(hue,0.88,1.0);
                    break;
                }
                case PULSE: {
                    double weight=0.5+0.5*Math.sin(phase*Math.PI*2);
                    rgb=blend(config.secondary,config.primary,weight);
                    break;
                }
                case SPARKLE: {
                    // Stable for the current time bucket and frame-independent of other controllers.
                    int hash=mix(i,tick);
                    int sparkle=(hash & 255);
                    double amount=sparkle<16 ? (sparkle<4?1.0:0.62) : 0;
                    rgb=blend(config.secondary,config.primary,amount);
                    break;
                }
                default: rgb=config.primary;
            }
            out[i]=scale(rgb, config.brightness/100.0);
        }
        return out;
    }
    private static int mix(int pixel,long tick) {
        long value=(pixel*0x9E3779B9L) ^ ((tick+1)*0x7F4A7C15L);
        value ^= value>>>16;value*=0x85ebca6bL;value^=value>>>13;
        return (int)(value^(value>>>16));
    }
    static int blend(int from,int to,double weight) {
        double t=Math.max(0,Math.min(1,weight));
        return 0xFF000000 | (mixChannel(from>>>16,to>>>16,t)<<16) |
                (mixChannel(from>>>8,to>>>8,t)<<8) | mixChannel(from,to,t);
    }
    private static int mixChannel(int a,int b,double t){return limit((int)Math.round((a&255)*(1-t)+(b&255)*t),0,255);}
    static int scale(int rgb,double level) {
        return 0xFF000000 | (limit((int)Math.round(((rgb>>>16)&255)*level),0,255)<<16) |
                (limit((int)Math.round(((rgb>>>8)&255)*level),0,255)<<8) |
                limit((int)Math.round((rgb&255)*level),0,255);
    }
    private static int hsv(double hue,double saturation,double value) {
        double chroma=value*saturation;
        double x=chroma*(1-Math.abs((hue/60)%2-1));
        double m=value-chroma;
        double r=0,g=0,b=0;
        if(hue<60){r=chroma;g=x;}
        else if(hue<120){r=x;g=chroma;}
        else if(hue<180){g=chroma;b=x;}
        else if(hue<240){g=x;b=chroma;}
        else if(hue<300){r=x;b=chroma;}
        else{r=chroma;b=x;}
        return 0xFF000000 | (limit((int)Math.round((r+m)*255),0,255)<<16) |
                (limit((int)Math.round((g+m)*255),0,255)<<8) |limit((int)Math.round((b+m)*255),0,255);
    }
    private static int limit(int x,int min,int max){return Math.min(max,Math.max(min,x));}
}
