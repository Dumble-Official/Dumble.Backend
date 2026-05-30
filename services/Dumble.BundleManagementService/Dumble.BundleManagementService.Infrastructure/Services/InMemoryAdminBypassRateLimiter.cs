using System.Collections.Concurrent;
using Dumble.BundleManagementService.Application.Contracts;
using Microsoft.Extensions.Options;

namespace Dumble.BundleManagementService.Infrastructure.Services;

public sealed class AdminBypassRateLimiterOptions
{
    public int MaxRequestsPerMinute { get; set; } = 10;
}

internal sealed class InMemoryAdminBypassRateLimiter(IOptions<AdminBypassRateLimiterOptions> options)
    : IAdminBypassRateLimiter, IDisposable
{
    private readonly ConcurrentDictionary<string, ConcurrentQueue<DateTime>> _windows = new();
    private readonly TimeSpan _window = TimeSpan.FromMinutes(1);
    private readonly int _maxPerWindow = options.Value.MaxRequestsPerMinute;
    private readonly object _lock = new();

    public bool IsAllowed(string adminId, string actionType)
    {
        var key = $"{adminId}:{actionType}";
        var now = DateTime.UtcNow;

        var queue = _windows.GetOrAdd(key, _ => new ConcurrentQueue<DateTime>());

        lock (_lock)
        {
            while (queue.TryPeek(out var ts) && now - ts > _window)
                queue.TryDequeue(out _);

            if (queue.Count >= _maxPerWindow)
                return false;

            queue.Enqueue(now);
            return true;
        }
    }

    public void Dispose()
    {
        _windows.Clear();
    }
}
