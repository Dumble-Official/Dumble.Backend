using Dumble.BundleManagementService.Application.Contracts.Repositories;
using Dumble.BundleManagementService.Domain.BundleAggregate;
using Dumble.BundleManagementService.Domain.BundleAggregate.ValueObjects;
using Dumble.BundleManagementService.Domain.Specifications.Bundles;
using MediatR;

namespace Dumble.BundleManagementService.Application.Features.Bundles.Queries.GetAllBundles;

internal sealed class GetAllBundlesQueryHandler(
    IGenericRepository<Bundle, BundleId> bundlesRepository) : IRequestHandler<GetAllBundlesQuery, GetAllBundlesResult>
{
    public async Task<GetAllBundlesResult> Handle(GetAllBundlesQuery request, CancellationToken cancellationToken)
    {
        var pageIndex = request.PageIndex < 1 ? 1 : request.PageIndex;
        var pageSize = request.PageSize is < 1 or > 100 ? 20 : request.PageSize;

        System.Linq.Expressions.Expression<Func<Bundle, bool>> criteria =
            request.OwnerId is { } ownerId
                ? b => b.OwnerId.Value == ownerId
                : _ => true;

        var spec = new GetAllBundlesSpecifications(pageIndex, pageSize, criteria);
        var bundles = await bundlesRepository.ListAsync(spec);
        var total = await bundlesRepository.Count(criteria);

        var items = bundles.Select(b => new BundleListItem(
            b.Id.Value,
            b.OwnerId.Value,
            b.Images.Select(i => i.Value).ToList(),
            b.Name.Value,
            b.Description.Value,
            b.Price.Value,
            b.ExpiresOn,
            b.Status.ToString(),
            b.Viewers.Count
        )).ToList();

        return new GetAllBundlesResult(items, total);
    }
}
