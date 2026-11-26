/* Nokia Smart Messaging basic/temporary ringing tones to Standard MIDI.
 * Copyright (C) 2026. GPL-2.0-only, as part of Bittboy J2ME.
 * Format: Nokia Smart Messaging Specification 3.0 section 3.6.
 */
package com.nokia.mid.sound;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

final class NokiaToneMidi {
    private final byte[] data;
    private int bit, scale = 1, style, volume = 100, pending;
    private final int[] positions = {-1,-1,-1,-1};
    private final int[] lengths = new int[4];
    private final ByteArrayOutputStream track = new ByteArrayOutputStream();
    private static final int[] BPM = {25,28,31,35,40,45,50,56,63,70,80,90,100,112,125,140,160,180,200,225,250,285,320,355,400,450,500,565,635,715,800,900};
    private int eventCount;
    private NokiaToneMidi(byte[] data) { this.data = data; }
    private int bits(int n) throws IOException {
        if (n < 0 || bit + n > data.length * 8) throw new IOException("OTT truncado");
        int value = 0;
        for (int i=0;i<n;i++) { value = (value << 1) | ((data[bit >>> 3] >>> (7-(bit & 7))) & 1); bit++; }
        return value;
    }
    private void variable(int value) {
        int buffer=value & 127;
        while ((value >>>= 7) != 0) buffer=(buffer << 8) | ((value & 127) | 128);
        for (;;) { track.write(buffer & 255); if ((buffer & 128) == 0) break; buffer >>>= 8; }
    }
    private void event(int status, int a, int b) throws IOException {
        if (++eventCount > 30000) throw new IOException("OTT longo demais");
        variable(pending); pending=0; track.write(status); track.write(a); if (b>=0) track.write(b);
    }
    private void tempo(int index) {
        int us=60000000 / BPM[index]; variable(pending);pending=0;
        track.write(0xff);track.write(0x51);track.write(3);track.write(us >>> 16);track.write(us >>> 8);track.write(us);
    }
    private void instruction() throws IOException {
        int cmd=bits(3);
        if (cmd==1) {
            int note=bits(4), dur=bits(3), spec=bits(2);
            if (dur>5) throw new IOException("Duração OTT inválida");
            int ticks=384 >> dur;
            if (spec==1) ticks=ticks*3/2; else if (spec==2) ticks=ticks*7/4; else if (spec==3) ticks=ticks*2/3;
            if (note==0 || note>12) {pending+=ticks;return;}
            int pitch=60+scale*12+note-1;
            event(0x90,pitch,volume);
            int sounding=style==2 ? ticks/2 : style==1 ? ticks : ticks*20/21;
            pending+=sounding;event(0x80,pitch,0);pending+=ticks-sounding;
        } else if (cmd==2) scale=bits(2);
        else if (cmd==3) style=bits(2);
        else if (cmd==4) tempo(bits(5));
        else if (cmd==5) {volume=bits(4)*127/15;}
        else throw new IOException("Instrução OTT inválida: "+cmd);
    }
    private void pattern() throws IOException {
        if (bits(3)!=0) throw new IOException("Cabeçalho de padrão OTT inválido");
        int id=bits(2), loops=bits(4), count=bits(8), resume;
        if (loops==15) loops=0; // An infinite pattern is rendered once; Sound.play controls outer loops.
        if (count==0) {
            if (positions[id]<0) throw new IOException("Padrão OTT não definido");
            resume=bit;
            for (int repeat=0;repeat<=loops;repeat++) {bit=positions[id];for(int i=0;i<lengths[id];i++)instruction();}
            bit=resume;
        } else {
            positions[id]=bit;lengths[id]=count;resume=bit;
            for (int repeat=0;repeat<=loops;repeat++) {bit=resume;for(int i=0;i<count;i++)instruction();}
        }
    }
    private byte[] convert() throws IOException {
        int commands=bits(8);
        if (commands==0) throw new IOException("OTT sem comandos");
        int first=bits(8);
        if (first==0x4a) { if (bits(7)!=0x1d) throw new IOException("Comando OTT não suportado"); }
        else if (first!=0x3a) throw new IOException("Cabeçalho OTT não suportado");
        int kind=bits(3);
        if (kind==1) {int length=bits(4);for(int i=0;i<length;i++)bits(8);}
        else if (kind!=2) throw new IOException("Tipo OTT não suportado: "+kind);
        int count=bits(8);
        event(0xc0,80,-1);
        for(int i=0;i<count;i++)pattern();
        variable(pending);track.write(0xff);track.write(0x2f);track.write(0);
        ByteArrayOutputStream midi=new ByteArrayOutputStream();
        write32(midi,0x4d546864);write32(midi,6);write16(midi,0);write16(midi,1);write16(midi,96);
        write32(midi,0x4d54726b);byte[] t=track.toByteArray();write32(midi,t.length);midi.write(t,0,t.length);
        return midi.toByteArray();
    }
    private static void write16(ByteArrayOutputStream out,int value){out.write(value>>>8);out.write(value);}
    private static void write32(ByteArrayOutputStream out,int value){out.write(value>>>24);out.write(value>>>16);out.write(value>>>8);out.write(value);}
    static byte[] fromOtt(byte[] bytes) throws IOException {
        NokiaToneMidi decoder = new NokiaToneMidi(bytes);
        if (bytes.length >= 5 && (bytes[0] & 255) + ((bytes[1] & 255) << 8) == bytes.length - 2 && bytes[2] == 2 && bytes[3] == 0x4a)
            decoder.bit = 16;
        return decoder.convert();
    }
    static byte[] singleTone(int frequency,long duration) throws IOException {
        if (frequency<=0 || duration<=0 || duration>60000) throw new IOException("Tom inválido");
        int note=(int)(69+12*Math.log(frequency/440.0)/Math.log(2)+0.5);
        if (note<0) note=0;if (note>127) note=127;
        NokiaToneMidi out=new NokiaToneMidi(new byte[0]);out.event(0xc0,80,-1);out.event(0x90,note,100);
        out.pending=(int)(duration*96/500);if(out.pending<1)out.pending=1;out.event(0x80,note,0);
        out.track.write(0);out.track.write(0xff);out.track.write(0x2f);out.track.write(0);
        ByteArrayOutputStream midi=new ByteArrayOutputStream();write32(midi,0x4d546864);write32(midi,6);write16(midi,0);write16(midi,1);write16(midi,96);write32(midi,0x4d54726b);
        byte[] t=out.track.toByteArray();write32(midi,t.length);midi.write(t,0,t.length);return midi.toByteArray();
    }
}
