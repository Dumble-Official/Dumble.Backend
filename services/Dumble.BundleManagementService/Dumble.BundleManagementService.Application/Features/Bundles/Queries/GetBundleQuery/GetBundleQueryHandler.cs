using Dumble.BundleManagementService.Application.Contracts.Repositories;
using Dumble.BundleManagementService.Application.Identity;
using Dumble.BundleManagementService.Domain.BundleAggregate;
using Dumble.BundleManagementService.Domain.BundleAggregate.ValueObjects;
using Dumble.SharedKernel.Contracts;
using MediatR;

namespace Dumble.BundleManagementService.Application.Features.Bundles.Queries.GetBundleQuery;

internal sealed class GetBundleQueryHandler(
    ILogger<GetBundleQueryHandler> logger,
    IGenericRepository<Bundle, BundleId> bundlesRepository,
    ILoggedInUserService loggedInUserService) : IRequestHandler<GetBundleQuery, Bundle>
{
    public async Task<GetBundleResult> Handle(GetBundleQuery request, CancellationToken cancellationToken)
    {
        var bundle = await bundlesRepository.Get(BundleId.Create(request.Id))
            ?? throw new KeyNotFoundException($"Bundle {request.Id} not found");

        var currentUser = loggedInUserService.GetCurrentUser();
        var accountId = AccountId.Create(AccountIdentity.ToAccountGuid(currentUser.Id));

        if (bundle.View(accountId))
        {
            bundlesRepository.Update(bundle);
            await bundlesRepository.CompleteAsync();
        }

        return bundle;
    }
}
