using Dumble.BundleManagementService.Application.Contracts;
using Dumble.BundleManagementService.Application.Contracts.Repositories;
using Dumble.BundleManagementService.Domain.BundleAggregate;
using Dumble.BundleManagementService.Domain.BundleAggregate.ValueObjects;
using Dumble.BundleManagementService.Domain.CategoryAggregate;
using Dumble.BundleManagementService.Domain.CategoryAggregate.ValueObjects;
using MediatR;

namespace Dumble.BundleManagementService.Application.Features.Bundles.Queries.GetBundleQuery;

internal sealed class GetBundleQueryHandler(
    IGenericRepository<Bundle, BundleId> bundlesRepository,
    IGenericRepository<Category, CategoryId> categoryRepository) : IRequestHandler<GetBundleQuery, GetBundleResult>
{
    public async Task<GetBundleResult> Handle(GetBundleQuery request, CancellationToken cancellationToken)
    {
        var bundle = await bundlesRepository.Get(BundleId.Create(request.Id))
            ?? throw new KeyNotFoundException($"Bundle {request.Id} not found");

        if (request.ViewerExternalId is { Length: > 0 } externalId)
        {
            var viewerId = AccountId.Create(AccountIdentity.ToAccountGuid(externalId));
            if (bundle.View(viewerId))
            {
                bundlesRepository.Update(bundle);
                await bundlesRepository.CompleteAsync();
            }
        }

        var category = await categoryRepository.Get(bundle.CategoryId);

        return new GetBundleResult(
            bundle.Id.Value,
            bundle.Images.Select(i => i.Value).ToList(),
            bundle.Name.Value,
            bundle.Description.Value,
            bundle.Price.Value,
            bundle.ExpiresOn,
            bundle.Status.ToString(),
            bundle.Viewers.Count,
            category?.Name.Value ?? string.Empty);
    }
}