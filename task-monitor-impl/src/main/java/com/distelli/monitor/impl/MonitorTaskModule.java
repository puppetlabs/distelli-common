package com.distelli.monitor.impl;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;
import com.distelli.persistence.TableDescription;
import com.distelli.monitor.TaskManager;
import com.distelli.monitor.TaskFunction;
import javax.inject.Singleton;
import com.google.inject.multibindings.MapBinder;
import com.distelli.monitor.Monitor;

public class MonitorTaskModule extends AbstractModule {
    @Override
    protected void configure() {
        MapBinder taskFunctionBinder = MapBinder.newMapBinder(binder(), String.class, TaskFunction.class);
        Multibinder tableBinder = Multibinder.newSetBinder(binder(), TableDescription.class);
        tableBinder.addBinding().toInstance(
            TaskManagerImpl.TasksTable.getTableDescription());
        tableBinder.addBinding().toInstance(
            TaskManagerImpl.LocksTable.getTableDescription());
        tableBinder.addBinding().toInstance(
            MonitorImpl.getTableDescription());
        taskFunctionBinder.addBinding(ReapMonitorTask.ENTITY_TYPE).to(
            ReapMonitorTask.class);
        bind(TaskManagerImpl.class).in(Singleton.class);
        bind(TaskManager.class).to(TaskManagerImpl.class);
        bind(MonitorImpl.class).in(Singleton.class);
        bind(Monitor.class).to(MonitorImpl.class);
    }
}
