package org.jacorb.notification.engine;

/*
 *        JacORB - a free Java ORB
 *
 *   Copyright (C) 1999-2003 Gerald Brose
 *
 *   This library is free software; you can redistribute it and/or
 *   modify it under the terms of the GNU Library General Public
 *   License as published by the Free Software Foundation; either
 *   version 2 of the License, or (at your option) any later version.
 *
 *   This library is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *   Library General Public License for more details.
 *
 *   You should have received a copy of the GNU Library General Public
 *   License along with this library; if not, write to the Free
 *   Software Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *
 */

import java.util.Collections;
import java.util.Hashtable;
import java.util.Map;

import org.jacorb.notification.interfaces.FilterStage;
import org.jacorb.notification.interfaces.Message;
import org.jacorb.notification.util.TaskExecutor;

import org.omg.CORBA.AnyHolder;
import org.omg.CosNotifyFilter.UnsupportedFilterableData;

/**
 * @author Alphonse Bendt
 * @version $Id$
 */

public class FilterProxySupplierTask extends AbstractFilterTask
{
    static class AlternateMessageMap {

        private Map alternateMessages_;

        public AlternateMessageMap() {
            this(new Hashtable());
        }

        AlternateMessageMap(Map m) {
            alternateMessages_ = m;
        }

        public Message getAlternateMessage(FilterStage s) {
            if (alternateMessages_.containsKey(s)) {
                return (Message)alternateMessages_.get(s);
            }
            return null;
        }

        public void putAlternateMessage(FilterStage s, Message e) {
            alternateMessages_.put(s, e);
        }

        public void clear() {
            alternateMessages_.clear();
        }
    }

    public static final AlternateMessageMap EMPTY_MAP =
        new AlternateMessageMap(Collections.EMPTY_MAP) {
            public void clear() {
            }
        };

    ////////////////////////////////////////

    AlternateMessageMap changedMessages_ = new AlternateMessageMap();

    private static int COUNT = 0;
    private int id_ = ++COUNT;

    ////////////////////////////////////////

    FilterProxySupplierTask(TaskExecutor te, TaskProcessor tp, TaskFactory tc) {
        super(te, tp, tc);
    }

    ////////////////////////////////////////

    public String toString()
    {
        return "[FilterProxySupplierTask#" + id_ + "]";
    }

    /**
     * Initialize this FilterOutgoingTask with the Configuration of
     * another FilterTask.
     */
    public void setFilterStage(AbstractFilterTask task) {
        arrayCurrentFilterStage_ = task.getFilterStageToBeProcessed();
    }


    public void reset() {
        super.reset();

        arrayCurrentFilterStage_ = null;
        changedMessages_.clear();
    }


    public void doWork() throws InterruptedException
    {
        filter();

        AbstractDeliverTask.scheduleTasks(taskFactory_.newPushToConsumerTask( this ) );

        dispose();
    }


    private Message updatePriority(int indexOfCurrentEvent, Message m) {
        AnyHolder _priorityFilterResult = new AnyHolder();

        Message _currentMessage = m;

        try {
            boolean priorityMatch =
                m.match(arrayCurrentFilterStage_[indexOfCurrentEvent].getPriorityFilter(),
                        _priorityFilterResult);

            if (priorityMatch) {
                _currentMessage = (Message)message_.clone();

                _currentMessage.setPriority(_priorityFilterResult.value.extract_long());

            }
        } catch (UnsupportedFilterableData e) {
            logger_.error("error evaluating PriorityFilter", e);
        }

        return _currentMessage;
    }


    private Message updateTimeout(int indexOfCurrentFilterStage, Message event) {
        AnyHolder _lifetimeFilterResult = new AnyHolder();
        Message _currentEvent = event;

        try {
            boolean lifetimeMatch =
                _currentEvent.match(arrayCurrentFilterStage_[indexOfCurrentFilterStage].getLifetimeFilter(),
                             _lifetimeFilterResult);

            if (lifetimeMatch && (_currentEvent == message_)) {
                _currentEvent = (Message)message_.clone();

                _currentEvent.setTimeout(_lifetimeFilterResult.value.extract_long());

            }

        } catch (UnsupportedFilterableData e) {
            logger_.error("error evaluating PriorityFilter", e);
        }

        return _currentEvent;
    }

    private void filter() {
        for (int x = 0; x < arrayCurrentFilterStage_.length; ++x) {
            boolean _forward = false;

            if (!arrayCurrentFilterStage_[x].isDisposed()) {

                Message _currentEvent = message_;

                if (arrayCurrentFilterStage_[x].hasPriorityFilter()) {
                    _currentEvent = updatePriority(x, _currentEvent);
                }

                if (arrayCurrentFilterStage_[x].hasLifetimeFilter()) {
                    _currentEvent = updateTimeout(x, _currentEvent);
                }

                if (_currentEvent != message_) {
                    changedMessages_.
                        putAlternateMessage(arrayCurrentFilterStage_[x],
                                                      _currentEvent);
                }

                _forward =
                    _currentEvent.match(arrayCurrentFilterStage_[x]);
            }

            if (_forward) {
                // the subsequent destination filters need to be eval'd
                addFilterStage(arrayCurrentFilterStage_[x].getSubsequentFilterStages());
            }
        }
    }
}
