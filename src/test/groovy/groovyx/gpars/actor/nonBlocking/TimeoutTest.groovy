// GPars - Groovy Parallel Systems
//
// Copyright © 2008-10  The original author or authors
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//       http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package groovyx.gpars.actor.nonBlocking

import groovyx.gpars.actor.Actor
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import static groovyx.gpars.actor.Actors.oldActor

/**
 *
 * @author Vaclav Pech
 * Date: Feb 27, 2009
 */
public class TimeoutTest extends GroovyTestCase {

    protected void setUp() {
        super.setUp();
    }

    public void testTimeout() {
        final def barrier = new CyclicBarrier(2)
        final AtomicBoolean timeoutFlag = new AtomicBoolean(false)

        oldActor {
            react(1000) {
                if (it == Actor.TIMEOUT) timeoutFlag.set(true)
                barrier.await()
                stop()
            }
        }

        barrier.await()
        assert timeoutFlag.get()
    }

    public void testTimeoutWithString() {
        final def barrier = new CyclicBarrier(2)
        final AtomicBoolean timeoutFlag = new AtomicBoolean(false)

        oldActor {
            react(1000) {
                if (it == 'TIMEOUT') timeoutFlag.set(true)
                barrier.await()
                stop()
            }
        }

        barrier.await()
        assert timeoutFlag.get()
    }

    public void testTimeoutWithLoop() {
        final def barrier = new CyclicBarrier(2)
        final AtomicBoolean timeoutFlag = new AtomicBoolean(false)

        oldActor {
            loop {
                react(1000) {
                    if (it == Actor.TIMEOUT) timeoutFlag.set(true)
                    barrier.await()
                    stop()
                }
            }
        }

        barrier.await()
        assert timeoutFlag.get()
    }

    public void testOnTimeoutHandler() {
        final def barrier = new CyclicBarrier(2)
        final AtomicBoolean timeoutFlag = new AtomicBoolean(false)

        def actor = oldActor {
            loop {
                react(1000) {
                    barrier.await()
                    stop()
                }
            }
        }

        actor.metaClass.onTimeout = {->
            timeoutFlag.set(true)
        }

        barrier.await()
        assert timeoutFlag.get()
    }

    public void testMessageBeforeTimeout() {
        final def barrier = new CyclicBarrier(2)
        final AtomicBoolean codeFlag = new AtomicBoolean(false)
        final AtomicBoolean nestedCodeFlag = new AtomicBoolean(false)
        final AtomicBoolean timeoutFlag = new AtomicBoolean(false)
        volatile def nestedMessage = null

        final def actor = oldActor {
            loop {
                barrier.await()
                react(5000) {
                    codeFlag.set(true)
                    react(1000) {
                        nestedCodeFlag.set(true)
                        nestedMessage = it
                        stop()
                    }
                }
            }
        }

        actor.metaClass {
            onTimeout = {-> timeoutFlag.set(true) }
            afterStop = {messages -> barrier.await() }
        }

        actor.send 'message'
        barrier.await()

        barrier.await()
        assert codeFlag.get()
        assert nestedCodeFlag.get()
        assert timeoutFlag.get()
        assert nestedMessage == Actor.TIMEOUT
    }

    public void testTimeoutInLoop() {
        final def barrier = new CyclicBarrier(2)
        final AtomicInteger codeCounter = new AtomicInteger(0)
        final AtomicBoolean timeoutFlag = new AtomicBoolean(false)

        final def actor = oldActor {
            loop {
                barrier.await()
                react(1000) {
                    codeCounter.incrementAndGet()
                }
            }
        }

        actor.metaClass {
            onTimeout = {-> timeoutFlag.set(true)}
        }

        actor.send 'message'
        barrier.await()
        barrier.await()

        barrier.await()
        actor.stop()
        assertEquals(2, codeCounter.get())
        assert timeoutFlag.get()
    }

    public void testExceptionInTimeout() {
        final def barrier = new CyclicBarrier(2)
        final AtomicInteger codeCounter = new AtomicInteger(0)
        final AtomicBoolean exceptionFlag = new AtomicBoolean(false)

        final def actor = oldActor {
            barrier.await()
            react(3000) {
                codeCounter.incrementAndGet()
            }
        }

        actor.metaClass {
            onException = {exceptionFlag.set(true); barrier.await()}
        }

        barrier.await()
        actor.stop()
        barrier.await()

        assertEquals(0, codeCounter.get())
        assert exceptionFlag.get()
        actor.join()
        assert !actor.isActive()

    }
}
