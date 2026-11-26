/*
 * MIDPath - Copyright (C) 2006-2008 Guillaume Legris, Mathieu Legris
 * 
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License version
 * 2 only, as published by the Free Software Foundation. 
 * 
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License version 2 for more details. 
 * 
 * You should have received a copy of the GNU General Public License
 * version 2 along with this work; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA 
 */
package org.thenesis.midpath.mmedia;

import javax.microedition.media.PlayerListener;

import sdljava.SDLException;
import sdljava.SDLMain;
import sdljava.mixer.SDLMixer;

public abstract class SDLPlayer extends BasicPlayer{
	
	static {
		try {
			SDLMain.init(SDLMain.SDL_INIT_AUDIO);
			//SDLMixer.openAudio(22050, SDLAudio.AUDIO_S16SYS, 1, 8192);
			//SDLMixer.openAudio(16384, SDLAudio.AUDIO_S16SYS, 1, 8192);
		} catch (SDLException e) {
			e.printStackTrace();
		}
	}
	
	
	protected abstract boolean isEndOfMediaReached();
    
    class EndOfMediaChecker implements Runnable {
		
		private volatile boolean running = true;
		
		public void run() {
			
			try {
                // Allow BasicPlayer.start() to finish setting STARTED before
                // the checker reports a very short sound's end.
                Thread.sleep(20);
				while(running) {
					if(isEndOfMediaReached() && running) {
						running = false;
                        // BasicPlayer's event dispatcher owns the state change
                        // and loop handling; changing state here skips looping.
						sendEvent(PlayerListener.END_OF_MEDIA, new Long(-1));
						break;
					}
					Thread.sleep(50);
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
			
		}
		
		public synchronized void start() {
			new Thread(this).start();
		}
		
		public synchronized void stop() {
			running = false;
		}
		
	}
	

}
