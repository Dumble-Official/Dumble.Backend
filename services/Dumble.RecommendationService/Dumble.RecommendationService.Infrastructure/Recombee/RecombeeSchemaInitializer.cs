using Dumble.RecommendationService.Application.Contracts;
using Microsoft.Extensions.Hosting;
using Microsoft.Extensions.Logging;

namespace Dumble.RecommendationService.Infrastructure.Recombee;

/// <summary>
/// Ensures the Recombee item-property schema exists once at startup. Best-effort: a failure
/// is logged but does not stop the service (cascadeCreate still creates items; the schema can
/// also be created out of band in the Recombee console).
/// </summary>
public sealed class RecombeeSchemaInitializer : IHostedService
{
    private readonly IRecombeeClient _client;
    private readonly ILogger<RecombeeSchemaInitializer> _logger;

    public RecombeeSchemaInitializer(IRecombeeClient client, ILogger<RecombeeSchemaInitializer> logger)
    {
        _client = client;
        _logger = logger;
    }

    public async Task StartAsync(CancellationToken cancellationToken)
    {
        try
        {
            await _client.EnsureSchemaAsync(cancellationToken);
            _logger.LogInformation("Recombee item schema ensured");
        }
        catch (Exception ex)
        {
            _logger.LogWarning(ex, "Could not ensure the Recombee item schema at startup; continuing");
        }
    }

    public Task StopAsync(CancellationToken cancellationToken) => Task.CompletedTask;
}
