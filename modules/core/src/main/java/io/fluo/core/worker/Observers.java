/*
 * Copyright 2014 Fluo authors (see AUTHORS)
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package io.fluo.core.worker;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.fluo.api.config.ObserverConfiguration;
import io.fluo.api.data.Column;
import io.fluo.api.observer.Observer;
import io.fluo.core.impl.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Observers implements AutoCloseable {

  private static final Logger log = LoggerFactory.getLogger(Observers.class);

  private Environment env;
  Map<Column, List<Observer>> observers = new HashMap<>();

  private List<Observer> getObserverList(Column col) {
    List<Observer> observerList;
    synchronized (observers) {
      observerList = observers.get(col);
      if (observerList == null) {
        observerList = new ArrayList<>();
        observers.put(col, observerList);
      }
    }
    return observerList;
  }

  public Observers(Environment env) {
    this.env = env;
  }

  public Observer getObserver(Column col) {

    List<Observer> observerList;
    observerList = getObserverList(col);

    synchronized (observerList) {
      if (observerList.size() > 0) {
        return observerList.remove(observerList.size() - 1);
      }
    }

    Observer observer = null;

    ObserverConfiguration observerConfig = env.getObservers().get(col);
    if (observerConfig == null) {
      observerConfig = env.getWeakObservers().get(col);
    }

    if (observerConfig != null) {
      try {
        observer =
            Class.forName(observerConfig.getClassName()).asSubclass(Observer.class).newInstance();
        observer.init(new ObserverContext(env, observerConfig.getParameters()));
      } catch (RuntimeException e) {
        throw e;
      } catch (Exception e) {
        throw new RuntimeException(e);
      }

      if (!observer.getObservedColumn().getColumn().equals(col)) {
        throw new IllegalStateException("Mismatch between configured column and class column "
            + observerConfig.getClassName() + " " + col + " "
            + observer.getObservedColumn().getColumn());
      }
    }

    return observer;
  }

  public void returnObserver(Observer observer) {
    List<Observer> olist = getObserverList(observer.getObservedColumn().getColumn());
    synchronized (olist) {
      olist.add(observer);
    }
  }

  @Override
  public void close() {
    if (observers == null) {
      return;
    }

    synchronized (observers) {
      for (List<Observer> olist : observers.values()) {
        synchronized (olist) {
          for (Observer observer : olist) {
            try {
              observer.close();
            } catch (Exception e) {
              log.error("Failed to close observer", e);
            }
          }
          olist.clear();
        }
      }
    }

    observers = null;
  }
}