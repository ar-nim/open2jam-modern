package org.open2jam.render;

import org.open2jam.render.entities.CompositeEntity;
import org.open2jam.render.entities.Entity;

/**
 * Optimized wrapper around the matrix of entities used by the render to manage
 * entities across layers.
 *
 * Uses flat arrays instead of LinkedList for better cache locality and zero
 * allocation during iteration. Employs swap-remove for O(1) removals.
 *
 * @author fox
 */
class EntityMatrix
{
    /** Flat array storage for each layer */
    private EntityArray[] layers;
    private int maxLayer = 0;

    public EntityMatrix()
    {
        // Start with reasonable initial capacity (most skins use < 20 layers)
        layers = new EntityArray[16];
    }

    /**
     * Add the entity to the matrix. Special case for CompositeEntity - each
     * sub-entity is added separately.
     */
    public void add(Entity e)
    {
        if (e instanceof CompositeEntity) {
            for (Entity i : ((CompositeEntity) e).getEntityList())
                this.add(i);
        } else {
            int layer = e.getLayer();
            
            // Ensure we have enough layers
            if (layer >= layers.length) {
                // Grow array - double size or reach needed layer
                int newSize = Math.max(layers.length * 2, layer + 1);
                EntityArray[] newLayers = new EntityArray[newSize];
                System.arraycopy(layers, 0, newLayers, 0, layers.length);
                layers = newLayers;
            }
            
            // Create layer if needed
            if (layers[layer] == null) {
                layers[layer] = new EntityArray();
                if (layer >= maxLayer) {
                    maxLayer = layer + 1;
                }
            }
            
            layers[layer].add(e);
        }
    }

    /**
     * Check if a specific layer is empty.
     */
    public boolean isEmpty(int layer)
    {
        return layer >= layers.length || layers[layer] == null || layers[layer].size() == 0;
    }

    /**
     * Process all layers and entities with the given processor.
     * Dead entities are automatically removed using shift-remove (preserves order).
     *
     * @param processor The processor to apply to each entity
     */
    public void processAll(EntityProcessor processor)
    {
        for (int i = 0; i < maxLayer; i++) {
            if (layers[i] == null) continue;
            
            EntityArray layer = layers[i];
            int size = layer.size();
            
            for (int j = 0; j < size; j++) {
                Entity e = layer.get(j);
                
                // Process the entity (move, update logic, mark as dead if needed)
                processor.process(e);
                
                // Remove dead entities using shift-remove (preserves order, matching LinkedList)
                if (e.isDead()) {
                    layer.shiftRemoveAt(j);
                    j--;  // Re-check same index (elements shifted left)
                    size--;  // Adjust size for this iteration
                }
            }
        }
    }

    /**
     * Process a specific layer with the given processor.
     * Dead entities are automatically removed using shift-remove (preserves order).
     *
     * @param layerIndex The layer index to process
     * @param processor The processor to apply to each entity
     */
    public void processLayer(int layerIndex, EntityProcessor processor)
    {
        if (layerIndex >= layers.length || layers[layerIndex] == null) {
            return;
        }

        EntityArray layer = layers[layerIndex];
        int size = layer.size();

        for (int j = 0; j < size; j++) {
            Entity e = layer.get(j);

            // Process the entity
            processor.process(e);

            // Remove dead entities using shift-remove (preserves order, matching LinkedList)
            if (e.isDead()) {
                layer.shiftRemoveAt(j);
                j--;  // Re-check same index (elements shifted left)
                size--;
            }
        }
    }

    /**
     * Functional interface for processing entities.
     */
    @FunctionalInterface
    interface EntityProcessor {
        void process(Entity e);
    }

    /**
     * Flat array-based entity collection with O(1) add and O(n) removal via shift.
     * Preserves entity order (important for consistent processing).
     */
    private static final class EntityArray
    {
        private Entity[] data;
        private int size;

        EntityArray()
        {
            // Initial capacity - most layers have few entities
            data = new Entity[64];
            size = 0;
        }

        int size()
        {
            return size;
        }

        Entity get(int index)
        {
            return data[index];
        }

        void add(Entity e)
        {
            // Grow if needed
            if (size >= data.length) {
                int newCapacity = data.length * 2;
                Entity[] newData = new Entity[newCapacity];
                System.arraycopy(data, 0, newData, 0, data.length);
                data = newData;
            }
            data[size++] = e;
        }

        /**
         * Remove entity at index by shifting subsequent elements.
         * Preserves order (matching LinkedList iterator behavior).
         */
        void shiftRemoveAt(int index)
        {
            if (index < 0 || index >= size) {
                return;
            }
            
            // Shift all elements after index one position left
            int numToShift = size - index - 1;
            if (numToShift > 0) {
                System.arraycopy(data, index + 1, data, index, numToShift);
            }
            data[size - 1] = null;  // Help GC
            size--;
        }

        void clear()
        {
            for (int i = 0; i < size; i++) {
                data[i] = null;  // Help GC
            }
            size = 0;
        }
    }
}
