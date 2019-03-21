package com.aricneto.twistytimer.items;

import com.aricneto.twistytimer.structures.RedBlackTree;

import androidx.annotation.Nullable;

import static com.aricneto.twistytimer.stats.AverageCalculator.DNF;
import static com.aricneto.twistytimer.stats.AverageCalculator.UNKNOWN;

/**
 * Stores a balanced tree, its sum, and its least and greatest times
 */
public class AverageComponent {
    private long               sum;
    private @Nullable Long     least;
    private @Nullable Long     greatest;
    private RedBlackTree<Long> tree;

    /**
     * Default constructor
     */
    public AverageComponent() {
        this.sum = UNKNOWN;
        this.least = null;
        this.greatest = null;
        this.tree = new RedBlackTree<>();
    }

    /**
     * Inserts an element into the tree and updates its sum and best/worst cache
     * @param val The value to be inserted
     */
    public void put(long val) {
        tree.add(val);
        addSum(val);

        // Update least/greatest caches if necessary
        if (least != null && val < least)
            least = val;
        if (greatest != null && val > greatest)
            greatest = val;
    }

    /**
     * Removes an element from tree and updates its sum and best/worst cache
     * @param val The value to be removed
     */
    public void remove(long val) {
        tree.remove(val);
        subSum(val);

        // Update least/greatest caches if necessary
        if (least != null && val == least)
            least = null;
        if (greatest != null && val == greatest)
            greatest = null;
    }

    /**
     * Gets the smallest element of the tree
     * @return The smallest element of the tree
     */
    public long getLeast() {
        // Cache request
        if (least == null)
            least = tree.getLeast();
        return least;
    }

    /**
     * Gets the biggest element of the tree
     * @return The biggest element of the tree
     */
    public long getGreatest() {
        // Cache request
        if (greatest == null)
            greatest = tree.getGreatest();
        return greatest;
    }

    /**
     * Gets the sum of all elements of the tree
     * @return The sum of all elements of the tree
     */
    public long getSum() {
        return sum;
    }

    public RedBlackTree<Long> getTree() {
        return tree;
    }

    /**
     * Adds a value to the total sum of the tree
     * @param val The value to be added
     */
    private void addSum(long val) {
        if (val != DNF)
            sum = (sum == UNKNOWN ? 0L : sum) + val;
    }

    /**
     * Removes a value from the total sum of the tree
     * @param val The value to be removed
     */
    private void subSum(long val) {
        if (val != DNF && sum != 0)
            sum = (sum == UNKNOWN ? 0L : sum) - val;
    }
}