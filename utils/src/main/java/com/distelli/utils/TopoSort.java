package com.distelli.utils;

import java.util.Objects;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.Map;
import java.util.HashSet;
import java.util.TreeSet;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.ArrayList;
import java.util.Iterator;

/**
 * Use this class to topologically sort a graph.
 *
 * Use the various add() methods to define the graph of T nodes.
 *
 * Then call sort() with a collection to be populated.
 */
public class TopoSort<T extends Comparable> {
    private Map<T, Set<T>> _dependencies = new TreeMap<>();
    private Set<T> _nodes = new TreeSet<>();

    public static class CycleDetectedException extends RuntimeException {
        private static <T> String toMessage(List<T> cycle) {
            StringBuilder sb = new StringBuilder();
            sb.append("cycle detected: [");
            boolean isFirst = true;
            for ( T elm : cycle ) {
                if ( isFirst ) {
                    isFirst = false;
                } else {
                    sb.append(", ");
                }
                sb.append(null == elm ? "null" : elm.toString());
            }
            sb.append("]");
            return sb.toString();
        }

        private List _cycle;
        public <T> CycleDetectedException(List<T> cycle) {
            super(toMessage(cycle));
            _cycle = cycle;
        }

        public List getCycle() {
            return _cycle;
        }
    }

    /**
     * @param node - a node that may not have any dependencies, but should
     *        be listed in the output.
     */
    public TopoSort<T> add(T node) {
        _nodes.add(node);
        return this;
    }

    /**
     * @param node - a node to add to the graph.
     *
     * @param dependencies - a collection of dependencies needed by node.
     */
    public TopoSort<T> add(T node, Collection<? extends T> dependencies) {
        _nodes.add(node);
        for ( T dependency : dependencies ) {
            _nodes.add(dependency);
            addEdge(node, dependency);
        }
        return this;
    }

    /**
     * @param node - the source node.
     *
     * @param dependency - the node that source depends on.
     */
    public TopoSort<T> add(T node, T dependency) {
        _nodes.add(node);
        _nodes.add(dependency);
        addEdge(node, dependency);
        return this;
    }

    @FunctionalInterface
    public static interface CollectionAdd<T> {
        public boolean add(T elm);
    }

    /**
     * @param out - The list to add() elements into to create the topo sort results.
     *
     * Typically we are interested in the reverse topo sort, so we have a method that
     * returns the reverse topo sort... (no method yet to return normal topo sort order).
     */
    public void reverseSort(CollectionAdd<T> out) {
        Set<T> todo = new TreeSet<>(_nodes);
        Set<T> temp = new HashSet<>();
        List<Frame<T>> stack = new ArrayList<>();

        /***********************************************************************

        Source: https://en.wikipedia.org/wiki/Topological_sorting
                Depth-first search

            L <- Empty list that will contain the sorted nodes
            while there are unmarked nodes do
                select an unmarked node n
                visit(n)

            function visit(node n)
                if n has a temporary mark then stop (not a DAG)
                if n is not marked (i.e. has not been visited yet) then
                    mark n temporarily
                    for each node m with an edge from n to m do
                        visit(m)
                    mark n permanently
                    unmark n temporarily
                    add n to head of L

        NOTE: visit() function was modified so it is not recursive. This was done
        so we can capture the stack of nodes and report on cycles found.

        */
        while ( ! todo.isEmpty() ) {
            stack.add(new Frame<T>(todo.iterator().next()));

            while ( ! stack.isEmpty() ) {
                Frame<T> frame = stack.get(stack.size()-1);
                if ( null == frame.iterator ) {
                    if ( temp.contains(frame.node) ) {
                        List<T> cycle = new ArrayList<>(stack.size());
                        boolean found = false;
                        for ( Frame<T> elm : stack ) {
                            if ( found ) {
                                cycle.add(elm.node);
                            } else if ( elm.node.equals(frame.node) ) {
                                found = true;
                            }
                        }
                        throw new CycleDetectedException(cycle);
                    }
                    if ( ! todo.contains(frame.node) ) {
                        stack.remove(stack.size()-1);
                        continue;
                    }
                    temp.add(frame.node);
                    todo.remove(frame.node);
                    Set<T> deps = _dependencies.get(frame.node);
                    frame.iterator = ( null == deps ) ? Collections.emptyIterator() : deps.iterator();
                }
                if ( !frame.iterator.hasNext() ) {
                    temp.remove(frame.node);
                    out.add(frame.node);
                    stack.remove(stack.size()-1);
                    continue;
                } else {
                    stack.add(new Frame<T>(frame.iterator.next()));
                }
            }
        }
    }

    /**
     * Removes a node from the graph, adding any transitive edges
     * in order to maintain the pre-existing relationships.
     */
    public TopoSort<T> remove(T node) {
        _nodes.remove(node);
        Set<T> deps = _dependencies.remove(node);

        // Find all consumers of node and add deps:
        for ( Set<T> otherDeps : _dependencies.values() ) {
            if ( otherDeps.remove(node) ) {
                if ( null != deps ) {
                    otherDeps.addAll(deps);
                }
            }
        }

        return this;
    }

    public Collection<T> getDependencies(T node) {
        Set<T> deps = _dependencies.get(node);
        if ( null == deps ) return Collections.emptySet();
        return Collections.unmodifiableCollection(deps);
    }

    private static class Frame<T> {
        public Iterator<T> iterator;
        public T node;
        public Frame(T node) {
            this.node = node;
        }
    }

    private void addEdge(T node, T dependency) {
        Set<T> dependencies = _dependencies.get(node);
        if ( null == dependencies ) {
            dependencies = new TreeSet<T>();
            _dependencies.put(node, dependencies);
        }
        dependencies.add(dependency);
    }
}
