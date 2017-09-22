package org.csap.agent.services;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.core.command.LogContainerResultCallback;

public class DockerContainerLogCallback  extends LogContainerResultCallback {
	protected final StringBuffer log = new StringBuffer();

	List<Frame> collectedFrames = new ArrayList<Frame>();

	boolean collectFrames = false;
	boolean collectLog = true;
	
	Writer writer = null;

	public DockerContainerLogCallback( ) {
		this( false );
	}

	public DockerContainerLogCallback( boolean collectFrames ) {
		this.collectFrames = collectFrames;
	}
	
	public DockerContainerLogCallback( Writer writer ) {
		this.writer = writer;
		this.collectLog = false;
	}

	@Override
	public void onNext ( Frame frame ) {
		if ( collectFrames )
			collectedFrames.add( frame );
		
		if ( collectLog )
			log.append( new String( frame.getPayload() ) );
		
		if ( writer != null ) {
			try {
				writer.write( new String( frame.getPayload() ) );
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		// logger.info( "payload: {}", new String( frame.getPayload() ));
	}

	@Override
	public String toString () {
		return log.toString();
	}

	public List<Frame> getCollectedFrames () {
		return collectedFrames;
	}
}