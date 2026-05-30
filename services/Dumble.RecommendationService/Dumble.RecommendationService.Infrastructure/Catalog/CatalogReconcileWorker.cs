using Dumble.RecommendationService.Application.Catalog;
using Dumble.RecommendationService.Infrastructure.Recombee;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.Hosting;
using Microsoft.Extensions.Logging;
using Microsoft.Extensions.Options;

namespace Dumble.RecommendationService.Infrastructure.Catalog;

/// <summary>
/// Periodically runs the catalog reconcile (D17) to heal any drift the event stream missed.
/// Resolves a scoped reconciler each cycle (the catalog HTTP client is scoped). A failed run is
/// logged and absorbed — the next cycle simply tries again. Low frequency by design (default
/// daily): this is a safety net, not the primary sync path.
/// </summary>
public sealed class CatalogReconcileWorker : BackgroundService
{
    private readonly IServiceScopeFactory _scopeFactory;
    private readonly RecombeeOptions _options;
    private readonly ILogger<CatalogReconcileWorker> _logger;

    public CatalogReconcileWorker(
        IServiceScopeFactory scopeFactory,
        IOptions<RecombeeOptions> options,
        ILogger<CatalogReconcileWorker> logger)
    {
        _scopeFactory = scopeFactory;
        _options = options.Value;
        _logger = logger;
    }

    protected override async Task ExecuteAsync(CancellationToken stoppingToken)
    {
        var interval = TimeSpan.FromHours(Math.Max(1, _options.ReconcileIntervalHours));
        _logger.LogInformation("Catalog reconcile worker started (interval {Interval}h)", interval.TotalHours);

        // Wait one interval before the first run so a fresh boot doesn't sweep while the event
        // stream is still catching up.
        while (!stoppingToken.IsCancellationRequested)
        {
            try
            {
                await Task.Delay(interval, stoppingToken);
            }
            catch (OperationCanceledException)
            {
                break;
            }

            try
            {
                using var scope = _scopeFactory.CreateScope();
                var reconciler = scope.ServiceProvider.GetRequiredService<CatalogReconciler>();
                await reconciler.ReconcileAsync(stoppingToken);
            }
            catch (OperationCanceledException) when (stoppingToken.IsCancellationRequested)
            {
                break;
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Catalog reconcile cycle failed; will retry next interval");
            }
        }

        _logger.LogInformation("Catalog reconcile worker stopping");
    }
}
