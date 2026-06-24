using Dumble.BundleManagementService.Application.Features.Bundles.Queries.GetAllBundles;
using Dumble.BundleManagementService.Contracts.Bundles.GetAllBundles;
using FastEndpoints;
using MediatR;

namespace Dumble.BundleManagementService.API.Endpoints.Bundles;

public sealed class GetAllBundlesEndpoint(ISender mediator)
    : EndpointWithoutRequest<GetAllBundlesResponse>
{
    public override void Configure()
    {
        Get("/api/bundles");
        AllowAnonymous();
        Options(o => o.WithTags("Bundles"));
    }

    public override async Task<GetAllBundlesResponse> ExecuteAsync(CancellationToken ct)
    {
        var pageIndex = Query<int?>("pageIndex", isRequired: false) ?? 1;
        var pageSize = Query<int?>("pageSize", isRequired: false) ?? 20;
        var ownerId = Query<Guid?>("ownerId", isRequired: false);

        var result = await mediator.Send(new GetAllBundlesQuery(pageIndex, pageSize, ownerId), ct);

        var items = result.Items
            .Select(i => new BundleListItemResponse(
                i.Id, i.OwnerId, i.Images, i.Name, i.Description, i.Price, i.ExpiresOn, i.Status, i.ViewCount))
            .ToList();

        return new GetAllBundlesResponse(items, result.TotalCount);
    }
}
