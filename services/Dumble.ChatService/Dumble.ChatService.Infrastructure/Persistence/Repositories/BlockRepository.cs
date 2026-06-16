using MongoDB.Driver;
using Dumble.ChatService.Application.Contracts;
using Dumble.ChatService.Domain.Models;

namespace Dumble.ChatService.Infrastructure.Persistence.Repositories;

public class BlockRepository : IBlockRepository
{
    private readonly MongoDbContext _context;

    public BlockRepository(MongoDbContext context)
    {
        _context = context;
    }

    public async Task BlockAsync(string blockerId, string blockedId, CancellationToken ct = default)
    {
        // Idempotent: don't insert a duplicate block.
        var exists = await _context.Blocks
            .Find(b => b.BlockerId == blockerId && b.BlockedId == blockedId)
            .AnyAsync(ct);
        if (exists) return;

        await _context.Blocks.InsertOneAsync(new UserBlock
        {
            BlockerId = blockerId,
            BlockedId = blockedId,
            CreatedAt = DateTime.UtcNow
        }, cancellationToken: ct);
    }

    public Task UnblockAsync(string blockerId, string blockedId, CancellationToken ct = default)
        => _context.Blocks.DeleteOneAsync(b => b.BlockerId == blockerId && b.BlockedId == blockedId, ct);

    public Task<bool> IsBlockedBetweenAsync(string userA, string userB, CancellationToken ct = default)
        => _context.Blocks.Find(b =>
                (b.BlockerId == userA && b.BlockedId == userB) ||
                (b.BlockerId == userB && b.BlockedId == userA))
            .AnyAsync(ct);

    public async Task<List<string>> GetBlockedIdsAsync(string blockerId, CancellationToken ct = default)
    {
        var blocks = await _context.Blocks.Find(b => b.BlockerId == blockerId).ToListAsync(ct);
        return blocks.Select(b => b.BlockedId).ToList();
    }
}
