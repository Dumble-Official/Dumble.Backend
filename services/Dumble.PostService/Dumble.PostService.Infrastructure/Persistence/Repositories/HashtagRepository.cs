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
        // Two concurrent posts can both observe a hashtag as missing, both
        // insert, and the unique index on Name will reject one of them.
        // Re-fetch on conflict and retry once — the second attempt will see
        // the row the winning request created and return it.
        for (var attempt = 0; attempt < 2; attempt++)
        {
            var existing = await _context.Hashtags
                .Where(h => names.Contains(h.Name))
                .ToListAsync(ct);

            var existingNames = existing.Select(h => h.Name).ToHashSet();
            var newNames = names.Where(n => !existingNames.Contains(n)).ToList();
            if (newNames.Count == 0) return existing;

            foreach (var name in newNames)
            {
                _context.Hashtags.Add(new Hashtag
                {
                    Id = Guid.NewGuid(),
                    Name = name,
                    UsageCount = 0
                });
            }

            try
            {
                await _context.SaveChangesAsync(ct);
                return await _context.Hashtags
                    .Where(h => names.Contains(h.Name))
                    .ToListAsync(ct);
            }
            catch (DbUpdateException) when (attempt == 0)
            {
                // Lost the insert race; clear our pending adds and retry once.
                foreach (var entry in _context.ChangeTracker.Entries<Hashtag>().ToList())
                {
                    if (entry.State == EntityState.Added)
                        entry.State = EntityState.Detached;
                }
            }
        }

        // Final fetch; whichever side won the insert, the rows exist now.
        return await _context.Hashtags
            .Where(h => names.Contains(h.Name))
            .ToListAsync(ct);
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
        var pattern = $"{LikeEscaping.EscapePattern(query)}%";
        return await _context.Hashtags
            .Where(h => EF.Functions.ILike(h.Name, pattern))
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
