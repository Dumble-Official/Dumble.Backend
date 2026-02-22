using Dumble.BundleManagementService.Application.Contracts;
using Dumble.BundleManagementService.Application.Contracts.Repositories;
using Dumble.BundleManagementService.Domain.BundleAggregate;
using Dumble.BundleManagementService.Domain.BundleAggregate.ValueObjects;
using MediatR;
using Microsoft.Extensions.Logging;

namespace Dumble.BundleManagementService.Application.Features.Bundles.Queries.GetBundleQuery;

internal sealed class GetBundleQueryHandler(ILogger<GetBundleQueryHandler> logger, 
                                            IGenericRepository<Bundle, BundleId> bundlesRepository,
                                            ILoggedInUserService loggedInUserService) : IRequestHandler<GetBundleQuery, Bundle>
{
    public async Task<Bundle> Handle(GetBundleQuery request, CancellationToken cancellationToken)
    {
        // 1. Fetch the bundle 
        var bundle = await bundlesRepository.Get(BundleId.Create(request.Id));
        
        // 3. Check if it is null or not
        if (bundle is null) throw new Exception();
        
        // 4. Add Viewer to the bundle 
        var currentUser = loggedInUserService.GetCurrentUser();

        var accountId = AccountId.Create(currentUser.Id);
        
        var isAdded = bundle.View(accountId);
        
        // 5. Persist the changes
        if (isAdded)
        {
            bundlesRepository.Update(bundle);

            await bundlesRepository.CompleteAsync();   
        }
        
        // 6. Return the bundle
        return bundle;
    }
}