using System.Linq.Expressions;
using Dumble.BundleManagementService.Application.Contracts.Repositories;
using Dumble.BundleManagementService.Domain.Common;
using Dumble.BundleManagementService.Infrastructure.Persistence.Data;
using Microsoft.EntityFrameworkCore;

namespace Dumble.BundleManagementService.Infrastructure.Persistence.Repositories;

internal sealed class GenericRepository<TEntity,TKey>(BundleManagementDbContext context) : IGenericRepository<TEntity, TKey>
    where TEntity : AggregateRoot<TKey>
    where TKey : ValueObject
{
    private readonly DbSet<TEntity> _dbSet = context.Set<TEntity>();
    
    public async Task<IEnumerable<TEntity>> GetAll(bool asNoTracking = true)
    {
        if (asNoTracking) return await _dbSet.AsNoTracking().ToListAsync();
        return await _dbSet.ToListAsync();
    }

    public async Task<int> Count(Expression<Func<TEntity, bool>> criteria)
    {
        return await _dbSet.CountAsync(criteria);
    }

    public async Task<TEntity?> Get(TKey id)
    {
        return await _dbSet.FirstOrDefaultAsync(e => e.Id.Equals(id));
    }

    public async Task Create(TEntity entity)
    {
        await _dbSet.AddAsync(entity);
    }

    public void Update(TEntity entity)
    {
        _dbSet.Update(entity);
    }

    public void Delete(TEntity entity)
    {
        _dbSet.Remove(entity);
    }

    public async Task<int> CompleteAsync()
    {
        return await context.SaveChangesAsync();
    }
}