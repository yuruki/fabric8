/*
 * Copyright (C) 2010-2011, FuseSource Corp.  All rights reserved
 *
 *    http://fusesource.com
 *
 * The software in this package is published under the terms of the
 * CDDL license, a copy of which has been included with this distribution
 * in the license.txt file
 */

package org.fusesource.fabric.apollo.amqp.protocol.interceptors.connection

import collection.mutable.Queue
import org.apache.activemq.apollo.util.Logging
import org.fusesource.fabric.apollo.amqp.codec.types._
import org.fusesource.fabric.apollo.amqp.protocol.utilities.execute
import org.fusesource.fabric.apollo.amqp.protocol.interfaces.FrameInterceptor

/**
 * Prevents frames on channel 0 from proceeding further in the receive interceptor
 * chain
 */
class ConnectionFrameBarrier extends FrameInterceptor[AMQPTransportFrame] with Logging {

  override protected def receive_frame(frame:AMQPTransportFrame, tasks: Queue[() => Unit]) = {
    if (frame.getChannel == 0) {
      execute(tasks)
    } else {
      incoming.receive(frame, tasks)
    }
  }
}