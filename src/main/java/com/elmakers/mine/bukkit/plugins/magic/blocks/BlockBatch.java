package com.elmakers.mine.bukkit.plugins.magic.blocks;

public interface BlockBatch {
	// Return the number of block sprocessed. The batch is assumed to be complete
	// if it returns 0.
	public int process(int maxBlocks);
}
