using Microsoft.EntityFrameworkCore;
using Dumble.PostService.Application.Contracts;
using Dumble.PostService.Domain.Entities;

namespace Dumble.PostService.Infrastructure.Persistence.Repositories;

public class HashtagRepository : IHashtagRepository
{
    private readonly PostDbContext _context;

    public HashtagRepository(PostDbContext context)
    {
        _context = context;
    }

    public async Task<Hashtag?> GetByNameAsync(string name, CancellationToken ct)
    {
        return await _context.Hashtags
            .FirstOrDefaultAsync(h => h.Name == name, ct);
    }

    public async Task<List<Hashtag>> GetOrCreateManyAsync(List<string> names, CancellationToken ct)
    {
        var existing = await _context.Hashtags
            .Where(h => names.Contains(h.Name))
            .ToListAsync(ct);

        var existingNames = existing.Select(h => h.Name).ToHashSet();
        var newNames = names.Where(n => !existingNames.Contains(n)).ToList();

        foreach (var name in newNames)
        {
            var hashtag = new Hashtag
            {
                Id = Guid.NewGuid(),
                Name = name,
                UsageCount = 0
            };
            _context.Hashtags.Add(hashtag);
            existing.Add(hashtag);
        }

        if (newNames.Count > 0)
            await _context.SaveChangesAsync(ct);

        return existing;
    }

    public async Task<List<Hashtag>> GetTrendingAsync(int limit, CancellationToken ct)
    {
        return await _context.Hashtags
            .OrderByDescending(h => h.UsageCount)
            .Take(limit)
            .ToListAsync(ct);
    }

    public async Task<List<Hashtag>> SearchAsync(string query, int limit, CancellationToken ct)
    {
        return await _context.Hashtags
            .Where(h => EF.Functions.ILike(h.Name, $"{query}%"))
            .OrderByDescending(h => h.UsageCount)
            .Take(limit)
            .ToListAsync(ct);
    }

    public async Task IncrementUsageCountAsync(List<Guid> hashtagIds, CancellationToken ct)
    {
        await _context.Hashtags
            .Where(h => hashtagIds.Contains(h.Id))
            .ExecuteUpdateAsync(s => s.SetProperty(h => h.UsageCount, h => h.UsageCount + 1), ct);
    }

    public async Task DecrementUsageCountAsync(List<Guid> hashtagIds, CancellationToken ct)
    {
        await _context.Hashtags
            .Where(h => hashtagIds.Contains(h.Id))
            .ExecuteUpdateAsync(s => s.SetProperty(h => h.UsageCount, h => h.UsageCount > 0 ? h.UsageCount - 1 : 0), ct);
    }
}
