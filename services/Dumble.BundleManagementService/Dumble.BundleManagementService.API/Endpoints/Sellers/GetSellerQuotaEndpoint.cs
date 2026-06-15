using Dumble.BundleManagementService.Application.Features.Bundles.Queries.GetSellerQuota;
using Dumble.BundleManagementService.Contracts.Sellers;
using FastEndpoints;
using MediatR;

namespace Dumble.BundleManagementService.API.Endpoints.Sellers;

/// <summary>
/// Service-to-service quota lookup the Subscription service calls before letting a seller create a
/// bundle (BundleManagementClient.getQuota). Not gateway-routed — internal callers only.
/// </summary>
public sealed class GetSellerQuotaEndpoint(ISender mediator)
    : EndpointWithoutRequest<QuotaResponse>
{
    public override void Configure()
    {
        Get("/api/sellers/{id}/quota");
        AllowAnonymous();
        Options(o => o.WithTags("Sellers"));
    }

    public override async Task<QuotaResponse> ExecuteAsync(CancellationToken ct)
    {
        var sellerId = Route<string>("id")!;
        var result = await mediator.Send(new GetSellerQuotaQuery(sellerId), ct);
        return new QuotaResponse(result.ActiveBundleCount, result.MaxAllowed, result.CanCreateMore);
    }
}
