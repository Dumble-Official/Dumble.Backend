using Dumble.RecommendationService.Application.Outbox;
using Dumble.RecommendationService.Infrastructure.Recombee;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.Hosting;
using Microsoft.Extensions.Logging;
using Microsoft.Extensions.Options;

namespace Dumble.RecommendationService.Infrastructure.Outbox;

/// <summary>
/// Background loop that drains the interaction outbox to Recombee. Resolves a scoped
/// processor each cycle (the store is DbContext-bound). When a full batch is drained it
/// loops immediately to catch up a backlog; otherwise it waits the configured interval.
/// A failed cycle is logged and absorbed — the rows stay queued and the next cycle retries.
/// </summary>
public sealed class OutboxFlushWorker : BackgroundService
{
    private readonly IServiceScopeFactory _scopeFactory;
    private readonly RecombeeOptions _options;
    private readonly ILogger<OutboxFlushWorker> _logger;

    public OutboxFlushWorker(
        IServiceScopeFactory scopeFactory,
        IOptions<RecombeeOptions> options,
        ILogger<OutboxFlushWorker> logger)
    {
        _scopeFactory = scopeFactory;
        _options = options.Value;
        _logger = logger;
    }

    protected override async Task ExecuteAsync(CancellationToken stoppingToken)
    {
        var interval = TimeSpan.FromSeconds(Math.Max(1, _options.FlushIntervalSeconds));
        _logger.LogInformation(
            "Outbox flush worker started (batch size {BatchSize}, interval {Interval}s)",
            _options.FlushBatchSize, interval.TotalSeconds);

        while (!stoppingToken.IsCancellationRequested)
        {
            var drainedFullBatch = false;
            try
            {
                using var scope = _scopeFactory.CreateScope();
                var processor = scope.ServiceProvider.GetRequiredService<OutboxFlushProcessor>();
                var flushed = await processor.FlushOnceAsync(_options.FlushBatchSize, stoppingToken);
                drainedFullBatch = flushed >= _options.FlushBatchSize;
            }
            catch (OperationCanceledException) when (stoppingToken.IsCancellationRequested)
            {
                break;
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Outbox flush cycle failed; will retry next interval");
            }

            if (drainedFullBatch)
                continue; // backlog likely remains — keep draining without waiting

            try
            {
                await Task.Delay(interval, stoppingToken);
            }
            catch (OperationCanceledException)
            {
                break;
            }
        }

        _logger.LogInformation("Outbox flush worker stopping");
    }
}
