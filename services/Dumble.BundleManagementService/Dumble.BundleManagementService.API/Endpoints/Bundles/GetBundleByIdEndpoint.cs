using Dumble.BundleManagementService.Application.Features.Bundles.Queries.GetBundleQuery;
using Dumble.BundleManagementService.Contracts.Bundles.GetBundle;
using FastEndpoints;
using MediatR;

namespace Dumble.BundleManagementService.API.Endpoints.Bundles;

public sealed class GetBundleByIdEndpoint(ISender mediator)
    : Endpoint<GetBundleRequest, GetBundleResponse>
{
    public override void Configure()
    {
        Get("/api/bundles/{id}");
        AllowAnonymous();
        Options(o => o.WithTags("Bundles"));
    }

    public override async Task<GetBundleResponse> ExecuteAsync(GetBundleRequest req, CancellationToken ct)
    {
        var viewerId = User.Identity?.IsAuthenticated == true
            ? User.FindFirst("userId")?.Value
            : null;

        var result = await mediator.Send(new GetBundleQuery(req.Id, viewerId), ct);

        return new GetBundleResponse(
            result.Id,
            result.Images,
            result.Name,
            result.Description,
            result.Price,
            result.ExpiresOn,
            result.Status,
            result.ViewCount,
            result.CategoryName,
            result.SellerId,
            result.SellerType,
            result.PriceCents,
            result.Currency,
            result.DurationDays,
            result.Active,
            result.Amenities,
            result.SellerUserId);
    }
}
