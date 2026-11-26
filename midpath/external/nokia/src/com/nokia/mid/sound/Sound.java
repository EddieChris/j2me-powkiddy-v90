
package com.nokia.mid.sound;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import javax.microedition.media.MediaException;
import javax.microedition.media.Player;
import javax.microedition.media.PlayerListener;
import javax.microedition.media.control.VolumeControl;
import javax.microedition.media.Manager;

/**
 * 
 * @author liang.wu
 *
 */
public class Sound
{

    public static final int SOUND_PLAYING = 0;
    public static final int SOUND_STOPPED = 1;
    public static final int SOUND_UNINITIALIZED = 3;
    
    public static final int FORMAT_TONE = 1;
    public static final int FORMAT_WAV = 5;

    public Player m_player;
    private Sound m_instance;
    private SoundListener listener;

    private int status;
    // Nokia gain ranges from 0 to 255, unlike MIDP VolumeControl (0..100).
    private int gain = 255;
    private int type;
    
    public int dataLen;

    // Small per-Sound LRU cache: compare full data, never just a hash.
    private static final int CACHE_LIMIT = 4;
    private static final int MAX_CACHE_INPUT = 16384;
    private final CachedSound[] cache = new CachedSound[CACHE_LIMIT];
    private int cacheClock;
    private static final class CachedSound {
        byte[] source;
        int type;
        int used;
        Player player;
    }
    private CachedSound findCached(byte[] data, int format) {
        for (int i = 0; i < cache.length; i++) {
            CachedSound entry = cache[i];
            if (entry == null || entry.type != format || entry.source.length != data.length) continue;
            int j = 0;
            while (j < data.length && entry.source[j] == data[j]) j++;
            if (j == data.length) return entry;
        }
        return null;
    }
    private boolean isCached(Player player) {
        for (int i=0; i<cache.length; i++)
            if (cache[i] != null && cache[i].player == player) return true;
        return false;
    }
    private void remember(byte[] source, int format, Player player) {
        if (source.length > MAX_CACHE_INPUT) return;
        int slot = -1;
        for (int i=0; i<cache.length; i++) {
            if (cache[i] == null) { slot=i; break; }
            if (slot < 0 || cache[i].used < cache[slot].used) slot=i;
        }
        if (cache[slot] != null) {
            try { cache[slot].player.close(); }
            catch (Exception e) { System.err.println("[Nokia som] Falha ao liberar cache: " + e); }
        }
        CachedSound entry = new CachedSound();
        entry.source = new byte[source.length];
        System.arraycopy(source, 0, entry.source, 0, source.length);
        entry.type = format; entry.player = player; entry.used = ++cacheClock;
        cache[slot] = entry;
    }


    public Sound(byte data[], int _type)
    {
        type = _type;
        if (data == null) throw new NullPointerException();
        dataLen = data.length;
        m_instance = this;
        init(data,_type);
    }
    
    public String getType()
    {
        if(type == FORMAT_TONE)
        {
            return "FORMAT_TONE";
        }
        else if(type == FORMAT_WAV)
        {
            return "FORMAT_WAV";
        }
        else
        {
            return null;
        }
    }
    /**
     * javax.microedition.media.Manager.playTone(int, int, int).
     * @param freq
     * @param duration
     */
    public Sound(int freq, long duration)
    {
        m_instance = this;
        init(freq, duration);
    }

    public static int getConcurrentSoundCount(int type)
    {
        return 1;
    }

    public int getGain()
    {
        return gain;
    }

    public int getState()
    {
        Player player = m_player;
        if (player == null || player.getState() == Player.CLOSED) return SOUND_UNINITIALIZED;
        return player.getState() == Player.STARTED ? SOUND_PLAYING : SOUND_STOPPED;
    }

    public static int[] getSupportedFormats()
    {
        int formats[] = {FORMAT_TONE,FORMAT_WAV};
        return formats;
    }

    public void init(byte data[], int _type)
    {
        if(data == null)
            throw new NullPointerException();
        resetPlayer();
        this.type = _type;
        dataLen = data.length;
        CachedSound hit = findCached(data, _type);
        if (hit != null) {
            hit.used = ++cacheClock;
            m_player = hit.player;
            status = SOUND_STOPPED;
            System.out.println("[Nokia som] cache: reutilizado");
            return;
        }
        byte[] sourceData = data;
        int sourceType = _type;
        int fingerprint = 1;
        for (int i = 0; i < data.length; i++) fingerprint = 31 * fingerprint + (data[i] & 255);
        System.out.println("[Nokia som] fonte=" + Integer.toHexString(fingerprint) + " tamanho=" + data.length + " tipo=" + _type);

        String type = "audio/midi";
        if (data.length >= 12 && data[0] == 'R' && data[1] == 'I' && data[2] == 'F' && data[3] == 'F') {
        	if (data[8] == 'W' && data[9] == 'A' && data[10] == 'V' && data[11] == 'E') {
	        	_type = FORMAT_WAV;
	        	type = "audio/x-wav";
        	}
        	else if (data[8] == 'R' && data[9] == 'M' && data[10] == 'I' && data[11] == 'D'){
        		type = "audio/midi";
        	}
        }
        else if (data.length >= 4 && data[0] == 'F' && data[1] == 'O' && data[2] == 'R' && data[3] == 'M') {
        	_type = FORMAT_WAV;
        	type = "audio/x-wav";
        }
        else if (data.length >= 4 && data[0] == 'M' && data[1] == 'T' && data[2] == 'h' && data[3] == 'd') {//MThd
        	type = "audio/midi";
        }
        
        if (_type == FORMAT_TONE && !(data.length >= 4 && data[0] == 'M' && data[1] == 'T' && data[2] == 'h' && data[3] == 'd')) {
            try {
                data = NokiaToneMidi.fromOtt(data);
                type = "audio/midi";
                System.out.println("[Nokia som] OTT convertido para MIDI (" + data.length + " bytes)");
            } catch (Exception e) {
                status = SOUND_UNINITIALIZED;
                System.err.println("[Nokia som] Falha ao converter OTT: " + e);
                return;
            }
        }

        try
        {
            InputStream is = new ByteArrayInputStream(data);
            m_player = Manager.createPlayer(is, type);
            m_player.addPlayerListener(new PlayerListener()
            {

                public void playerUpdate(Player player, String event, Object eventData)
                {
                   if(player == m_player && (event.equals(PlayerListener.STOPPED) || event.equals(PlayerListener.END_OF_MEDIA)))
                   {
                       status = SOUND_STOPPED;
                       if(listener != null)
                           listener.soundStateChanged(m_instance, SOUND_STOPPED);
                   }
                }
                
            });
            is.close();
            is = null;
            remember(sourceData, sourceType, m_player);
        }
        catch(Exception e)
        {
            m_player = null;
            status = SOUND_UNINITIALIZED;
            System.err.println("[Nokia som] Falha ao criar player: " + e);
            return;
        }
        status = SOUND_STOPPED;
    }
    
    public void init(int freq, long duration)
    {
       resetPlayer();
       type = FORMAT_TONE;
       try {
           byte[] midi = NokiaToneMidi.singleTone(freq, duration);
           m_player = Manager.createPlayer(new ByteArrayInputStream(midi), "audio/midi");
           status = SOUND_STOPPED;
       } catch (Exception e) {
           m_player = null;
           status = SOUND_UNINITIALIZED;
           System.err.println("[Nokia som] Falha ao criar tom: " + e);
       }
    }

    private void resetPlayer()
    {
        Player old = m_player;
        m_player = null;
        status = SOUND_UNINITIALIZED;
        if (old != null) {
            try {
                old.stop();
                if (!isCached(old)) old.close();
            } catch (Exception e) {
                System.err.println("[Nokia som] Falha ao parar player anterior: " + e);
            }
        }
    }

    public void play(int loop)
    {
        if (m_player == null) return;
        try {
            // play starts at the beginning; resume() keeps the old position.
            m_player.stop();
            if (m_player.getState() >= Player.REALIZED) {
                try { m_player.setMediaTime(0); } catch (Exception ignored) { }
            }
            m_player.setLoopCount(loop == 0 ? -1 : loop);
            resume();
        } catch (Exception e) {
            System.err.println("[Nokia som] Falha ao iniciar: " + e);
        }
    }

    public void release()
    {
        Player current = m_player;
        boolean cached = current != null && isCached(current);
        m_player = null;
        status = SOUND_UNINITIALIZED;
        if (current != null && !cached) {
            try { current.close(); } catch (Exception ignored) { }
        }
        for (int i=0; i<cache.length; i++) {
            if (cache[i] != null) {
                try { cache[i].player.close(); } catch (Exception ignored) { }
                cache[i] = null;
            }
        }
        if (listener != null) listener.soundStateChanged(this, SOUND_UNINITIALIZED);
    }

    public void resume()
    {        
    	if (m_player == null) return;
        try
        {
            m_player.start();
        }
        catch (Exception e)
        {
            System.err.println("[Nokia som] Falha ao reproduzir: " + e);
            return;
        }
        
        status = SOUND_PLAYING;
        applyGain();
        if(listener != null)
            listener.soundStateChanged(this, SOUND_PLAYING);
    }


    public void setGain(int level)
    {
        if (level < 0) level = 0;
        if (level > 255) level = 255;
        gain = level;
        // The player can still be UNREALIZED at game initialization.
        if (m_player != null && m_player.getState() == Player.STARTED) applyGain();
    }

    private void applyGain()
    {
        try {
            VolumeControl volume = (VolumeControl)m_player.getControl("VolumeControl");
            if (volume != null) volume.setLevel((gain * 100 + 127) / 255);
        } catch (Exception e) {
            System.err.println("[Nokia som] Falha ao aplicar volume: " + e);
        }
    }

    public void setSoundListener(SoundListener soundlistener)
    {
        listener = soundlistener;
    }

    public void stop()
    {       
    	if (m_player == null) return;
        try
        {
            m_player.stop();
        }
        catch (Exception e)
        {
        }
        
        status = SOUND_STOPPED;
        
        if(listener != null)
            listener.soundStateChanged(this, SOUND_STOPPED);
    }
    
}
